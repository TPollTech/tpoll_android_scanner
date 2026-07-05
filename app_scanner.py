import csv
import json
import os
import re
import subprocess
import sys
import threading
import time
import tkinter as tk
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path
from tkinter import ttk, messagebox, filedialog

APP_NAME = "TPoll Android App Scanner"
VERSION = "0.2.0"

DEFAULT_RULES = {
    "trusted_installers": [
        "com.android.vending",                # Google Play Store
        "com.sec.android.app.samsungapps",   # Galaxy Store
        "com.amazon.venezia",                # Amazon Appstore
        "com.huawei.appmarket",              # Huawei AppGallery
        "com.xiaomi.mipicks",                # GetApps
        "com.heytap.market",                 # Oppo/Realme/OnePlus market
        "com.vivo.appstore",                 # Vivo App Store
    ],
    "suspicious_terms": [
        "clean", "cleaner", "phonecleaner", "junk", "cachecleaner", "cache",
        "boost", "booster", "speed", "optimizer", "optimiser", "ramclean",
        "battery", "batterysaver", "saver", "cooler", "antivirus", "security",
        "safe", "protect", "vpn", "proxy", "browser", "launcher", "keyboard",
        "wallpaper", "theme", "notification", "reward", "earn", "cash", "ads",
        "adservice", "permission", "accessibility", "scanner", "master", "turbo"
    ],
    "high_risk_permissions": {
        "android.permission.SYSTEM_ALERT_WINDOW": 35,
        "android.permission.REQUEST_INSTALL_PACKAGES": 35,
        "android.permission.QUERY_ALL_PACKAGES": 25,
        "android.permission.BIND_ACCESSIBILITY_SERVICE": 45,
        "android.permission.RECEIVE_BOOT_COMPLETED": 15,
        "android.permission.FOREGROUND_SERVICE": 10,
        "android.permission.READ_SMS": 30,
        "android.permission.SEND_SMS": 45,
        "android.permission.RECEIVE_SMS": 35,
        "android.permission.READ_CONTACTS": 25,
        "android.permission.WRITE_CONTACTS": 25,
        "android.permission.READ_PHONE_STATE": 20,
        "android.permission.CALL_PHONE": 25,
        "android.permission.POST_NOTIFICATIONS": 10,
        "android.permission.PACKAGE_USAGE_STATS": 35,
        "android.permission.MANAGE_EXTERNAL_STORAGE": 25,
        "android.permission.READ_EXTERNAL_STORAGE": 10,
        "android.permission.WRITE_EXTERNAL_STORAGE": 10,
        "android.permission.CAMERA": 8,
        "android.permission.RECORD_AUDIO": 12,
        "android.permission.ACCESS_FINE_LOCATION": 20,
        "android.permission.ACCESS_COARSE_LOCATION": 12,
    },
    "high_risk_appops": {
        "SYSTEM_ALERT_WINDOW": 35,
        "GET_USAGE_STATS": 35,
        "REQUEST_INSTALL_PACKAGES": 35,
        "ACCESS_NOTIFICATIONS": 20,
        "READ_CLIPBOARD": 20,
        "AUTO_REVOKE_PERMISSIONS_IF_UNUSED": 0
    }
}

RULES_PATH = Path(__file__).with_name("rules.json")


def load_rules():
    if RULES_PATH.exists():
        try:
            with open(RULES_PATH, "r", encoding="utf-8") as f:
                rules = json.load(f)
            merged = DEFAULT_RULES.copy()
            merged.update(rules)
            return merged
        except Exception:
            return DEFAULT_RULES
    else:
        try:
            with open(RULES_PATH, "w", encoding="utf-8") as f:
                json.dump(DEFAULT_RULES, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
        return DEFAULT_RULES


@dataclass
class AppFinding:
    package: str
    apk_path: str = ""
    installer: str = ""
    permissions: list = None
    appops: list = None
    score: int = 0
    level: str = "BAIXO"
    reasons: list = None

    def to_row(self):
        return {
            "nivel": self.level,
            "pontuacao": self.score,
            "pacote": self.package,
            "instalador": self.installer,
            "motivos": "; ".join(self.reasons or []),
            "permissoes_sensiveis": "; ".join(self.permissions or []),
            "appops": "; ".join(self.appops or []),
            "apk_path": self.apk_path,
        }


class AdbError(Exception):
    pass


class AdbClient:
    def __init__(self, adb_path="adb"):
        self.adb_path = adb_path

    def run(self, args, timeout=18):
        cmd = [self.adb_path] + args
        try:
            result = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )
        except FileNotFoundError:
            raise AdbError("ADB não encontrado. Instale o Android Platform Tools ou coloque adb.exe na pasta do programa.")
        except subprocess.TimeoutExpired:
            raise AdbError("Comando ADB demorou demais. Verifique cabo, autorização USB e tela do celular.")
        if result.returncode != 0:
            err = (result.stderr or result.stdout or "Erro desconhecido no ADB").strip()
            raise AdbError(err)
        return result.stdout.strip()

    def list_devices(self):
        out = self.run(["devices"], timeout=10)
        devices = []
        for line in out.splitlines()[1:]:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) >= 2:
                serial, status = parts[0], parts[1]
                devices.append((serial, status))
        return devices

    def shell(self, serial, args, timeout=18):
        return self.run(["-s", serial, "shell"] + args, timeout=timeout)

    def getprop(self, serial, prop):
        try:
            return self.shell(serial, ["getprop", prop], timeout=8).strip()
        except Exception:
            return ""

    def list_packages(self, serial, third_party_only=True):
        args = ["pm", "list", "packages", "-f"]
        if third_party_only:
            args.append("-3")
        out = self.shell(serial, args, timeout=30)
        packages = []
        for line in out.splitlines():
            line = line.strip()
            if not line.startswith("package:"):
                continue
            body = line[len("package:"):]
            if "=" in body:
                path, pkg = body.rsplit("=", 1)
            else:
                path, pkg = "", body
            if pkg:
                packages.append((pkg.strip(), path.strip()))
        return packages

    def installer(self, serial, package):
        # Newer Androids support this. Older versions may fail, so we fallback to dumpsys.
        try:
            out = self.shell(serial, ["cmd", "package", "get-install-source", package], timeout=10)
            for key in ["installerPackageName", "installingPackageName", "initiatingPackageName"]:
                m = re.search(rf"{key}:\s*([^\s]+)", out)
                if m and m.group(1) not in ("null", "None"):
                    return m.group(1).strip()
        except Exception:
            pass
        try:
            out = self.shell(serial, ["dumpsys", "package", package], timeout=15)
            for pattern in [r"installerPackageName=([^\s]+)", r"installer=([^\s]+)"]:
                m = re.search(pattern, out)
                if m and m.group(1) not in ("null", "None"):
                    return m.group(1).strip()
        except Exception:
            pass
        return "desconhecido"

    def dumpsys_package(self, serial, package):
        try:
            return self.shell(serial, ["dumpsys", "package", package], timeout=18)
        except Exception:
            return ""

    def appops(self, serial, package):
        try:
            return self.shell(serial, ["cmd", "appops", "get", package], timeout=12)
        except Exception:
            return ""

    def uninstall_user0(self, serial, package):
        return self.shell(serial, ["pm", "uninstall", "--user", "0", package], timeout=30)

    def force_stop(self, serial, package):
        return self.shell(serial, ["am", "force-stop", package], timeout=10)


def parse_sensitive_permissions(dumpsys_text, rules):
    found = set(re.findall(r"android\.permission\.[A-Z0-9_]+", dumpsys_text or ""))
    sensitive = [p for p in sorted(found) if p in rules["high_risk_permissions"]]
    return sensitive


def parse_risky_appops(appops_text, rules):
    found = []
    for op in rules["high_risk_appops"]:
        if re.search(rf"\b{re.escape(op)}\b", appops_text or "", re.IGNORECASE):
            found.append(op)
    return sorted(set(found))


def score_app(package, installer, permissions, appops, rules):
    pkg_lower = package.lower()
    reasons = []
    score = 0

    matched_terms = []
    normalized = re.sub(r"[^a-z0-9]+", "", pkg_lower)
    for term in rules["suspicious_terms"]:
        t = re.sub(r"[^a-z0-9]+", "", term.lower())
        if t and t in normalized:
            matched_terms.append(term)
    if matched_terms:
        add = min(40, 12 + len(matched_terms) * 6)
        score += add
        reasons.append("nome/pacote com termos suspeitos: " + ", ".join(sorted(set(matched_terms))[:8]))

    trusted = set(rules.get("trusted_installers", []))
    if installer in ("desconhecido", "", "null", "None"):
        score += 12
        reasons.append("origem/instalador desconhecido")
    elif installer not in trusted:
        score += 20
        reasons.append(f"instalado por origem não confiável na lista: {installer}")

    for perm in permissions:
        points = int(rules["high_risk_permissions"].get(perm, 0))
        if points:
            score += points
            reasons.append(f"permissão sensível: {perm.split('.')[-1]} (+{points})")

    for op in appops:
        points = int(rules["high_risk_appops"].get(op, 0))
        if points:
            score += points
            reasons.append(f"app-op sensível: {op} (+{points})")

    # Combinações bem comuns em adware/limpadores falsos.
    combo = set([p.split('.')[-1] for p in permissions]) | set(appops)
    if "SYSTEM_ALERT_WINDOW" in combo and ("RECEIVE_BOOT_COMPLETED" in combo or "FOREGROUND_SERVICE" in combo):
        score += 25
        reasons.append("combinação suspeita: sobrepor tela + iniciar/rodar em segundo plano")
    if "REQUEST_INSTALL_PACKAGES" in combo and "QUERY_ALL_PACKAGES" in combo:
        score += 20
        reasons.append("combinação suspeita: instalar APKs + enxergar outros apps")

    score = min(score, 100)
    if score >= 70:
        level = "ALTO"
    elif score >= 40:
        level = "MÉDIO"
    else:
        level = "BAIXO"
    if not reasons:
        reasons.append("nenhum sinal forte nas regras atuais")
    return score, level, reasons


class ScannerApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"{APP_NAME} v{VERSION}")
        self.geometry("1120x680")
        self.minsize(980, 560)
        self.rules = load_rules()
        self.adb_path = self.find_adb()
        self.adb = AdbClient(self.adb_path)
        self.device_serial = None
        self.findings = []
        self.scan_thread = None
        self.stop_flag = False

        self.create_widgets()
        self.after(200, self.refresh_devices)

    def find_adb(self):
        base = Path(__file__).resolve().parent
        candidates = [
            base / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"),
            base / ("adb.exe" if os.name == "nt" else "adb"),
            "adb",
        ]
        for c in candidates:
            if isinstance(c, Path):
                if c.exists():
                    return str(c)
            else:
                return c
        return "adb"

    def create_widgets(self):
        self.columnconfigure(0, weight=1)
        self.rowconfigure(3, weight=1)

        header = ttk.Frame(self, padding=10)
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(6, weight=1)

        ttk.Label(header, text="Dispositivo:").grid(row=0, column=0, padx=(0, 6))
        self.device_var = tk.StringVar()
        self.device_combo = ttk.Combobox(header, textvariable=self.device_var, width=34, state="readonly")
        self.device_combo.grid(row=0, column=1, padx=(0, 8))

        ttk.Button(header, text="Atualizar", command=self.refresh_devices).grid(row=0, column=2, padx=4)
        ttk.Button(header, text="Escanear apps", command=self.start_scan).grid(row=0, column=3, padx=4)
        ttk.Button(header, text="Exportar relatório", command=self.export_report).grid(row=0, column=4, padx=4)
        ttk.Button(header, text="Abrir regras", command=self.open_rules).grid(row=0, column=5, padx=4)

        self.third_party_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(header, text="Somente apps baixados", variable=self.third_party_var).grid(row=0, column=7, padx=4)

        info = ttk.Frame(self, padding=(10, 0, 10, 8))
        info.grid(row=1, column=0, sticky="ew")
        info.columnconfigure(0, weight=1)
        self.status_var = tk.StringVar(value="Conecte o celular com Depuração USB ativada e autorize a chave RSA na tela do aparelho.")
        ttk.Label(info, textvariable=self.status_var).grid(row=0, column=0, sticky="w")
        self.progress = ttk.Progressbar(info, mode="determinate")
        self.progress.grid(row=1, column=0, sticky="ew", pady=(6, 0))

        filters = ttk.Frame(self, padding=(10, 0, 10, 8))
        filters.grid(row=2, column=0, sticky="ew")
        ttk.Label(filters, text="Filtro:").pack(side="left")
        self.filter_var = tk.StringVar(value="TODOS")
        for text in ["TODOS", "ALTO", "MÉDIO", "BAIXO"]:
            ttk.Radiobutton(filters, text=text, value=text, variable=self.filter_var, command=self.populate_table).pack(side="left", padx=8)
        ttk.Button(filters, text="Parar app selecionado", command=self.force_stop_selected).pack(side="right", padx=4)
        ttk.Button(filters, text="Remover selecionado(s)", command=self.uninstall_selected).pack(side="right", padx=4)
        ttk.Button(filters, text="Selecionar alto risco", command=self.select_high_risk).pack(side="right", padx=4)
        ttk.Button(filters, text="Copiar comando", command=self.copy_command_selected).pack(side="right", padx=4)

        table_frame = ttk.Frame(self, padding=(10, 0, 10, 10))
        table_frame.grid(row=3, column=0, sticky="nsew")
        table_frame.columnconfigure(0, weight=1)
        table_frame.rowconfigure(0, weight=1)

        columns = ("level", "score", "package", "installer", "reasons")
        self.tree = ttk.Treeview(table_frame, columns=columns, show="headings", selectmode="extended")
        self.tree.heading("level", text="Risco")
        self.tree.heading("score", text="Pontos")
        self.tree.heading("package", text="Pacote")
        self.tree.heading("installer", text="Instalador")
        self.tree.heading("reasons", text="Motivos")
        self.tree.column("level", width=80, anchor="center")
        self.tree.column("score", width=70, anchor="center")
        self.tree.column("package", width=280)
        self.tree.column("installer", width=190)
        self.tree.column("reasons", width=500)
        self.tree.grid(row=0, column=0, sticky="nsew")
        self.tree.bind("<<TreeviewSelect>>", self.on_select)

        scroll_y = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        scroll_y.grid(row=0, column=1, sticky="ns")
        self.tree.configure(yscrollcommand=scroll_y.set)

        details_frame = ttk.LabelFrame(self, text="Detalhes do app selecionado", padding=10)
        details_frame.grid(row=4, column=0, sticky="ew", padx=10, pady=(0, 10))
        details_frame.columnconfigure(0, weight=1)
        self.details = tk.Text(details_frame, height=7, wrap="word")
        self.details.grid(row=0, column=0, sticky="ew")
        self.details.configure(state="disabled")

    def refresh_devices(self):
        try:
            devices = self.adb.list_devices()
        except AdbError as e:
            self.status_var.set(str(e))
            self.device_combo["values"] = []
            return
        labels = []
        self.devices = devices
        for serial, status in devices:
            labels.append(f"{serial}  [{status}]")
        self.device_combo["values"] = labels
        if labels:
            self.device_combo.current(0)
            self.status_var.set("Dispositivo encontrado. Se aparecer 'unauthorized', confirme a depuração USB no celular.")
        else:
            self.status_var.set("Nenhum dispositivo ADB encontrado.")

    def current_serial(self):
        idx = self.device_combo.current()
        if idx < 0 or not getattr(self, "devices", None):
            return None, None
        return self.devices[idx]

    def start_scan(self):
        serial, status = self.current_serial()
        if not serial:
            messagebox.showwarning("Sem dispositivo", "Conecte um celular Android com Depuração USB ativada.")
            return
        if status != "device":
            messagebox.showwarning("Dispositivo não autorizado", "Autorize a depuração USB na tela do celular e clique em Atualizar.")
            return
        if self.scan_thread and self.scan_thread.is_alive():
            messagebox.showinfo("Escaneando", "A varredura já está em andamento.")
            return
        self.device_serial = serial
        self.findings = []
        self.populate_table()
        self.details_set("")
        self.stop_flag = False
        self.scan_thread = threading.Thread(target=self.scan_worker, daemon=True)
        self.scan_thread.start()

    def scan_worker(self):
        serial = self.device_serial
        try:
            brand = self.adb.getprop(serial, "ro.product.brand")
            model = self.adb.getprop(serial, "ro.product.model")
            android = self.adb.getprop(serial, "ro.build.version.release")
            self.ui(lambda: self.status_var.set(f"Escaneando {brand} {model} | Android {android}..."))
            packages = self.adb.list_packages(serial, third_party_only=self.third_party_var.get())
            total = len(packages)
            self.ui(lambda: self.progress.configure(maximum=max(total, 1), value=0))
            for i, (pkg, path) in enumerate(packages, start=1):
                if self.stop_flag:
                    break
                self.ui(lambda p=pkg, i=i, total=total: self.status_var.set(f"Analisando {i}/{total}: {p}"))
                installer = self.adb.installer(serial, pkg)
                dumpsys = self.adb.dumpsys_package(serial, pkg)
                appops_text = self.adb.appops(serial, pkg)
                perms = parse_sensitive_permissions(dumpsys, self.rules)
                ops = parse_risky_appops(appops_text, self.rules)
                score, level, reasons = score_app(pkg, installer, perms, ops, self.rules)
                finding = AppFinding(
                    package=pkg,
                    apk_path=path,
                    installer=installer,
                    permissions=perms,
                    appops=ops,
                    score=score,
                    level=level,
                    reasons=reasons,
                )
                self.findings.append(finding)
                if i % 2 == 0 or i == total:
                    self.ui(lambda i=i: self.progress.configure(value=i))
                    self.ui(self.populate_table)
            self.findings.sort(key=lambda f: (f.score, f.package), reverse=True)
            self.ui(self.populate_table)
            high = sum(1 for f in self.findings if f.level == "ALTO")
            med = sum(1 for f in self.findings if f.level == "MÉDIO")
            self.ui(lambda: self.status_var.set(f"Varredura finalizada: {len(self.findings)} apps analisados | {high} alto risco | {med} médio risco."))
        except AdbError as e:
            self.ui(lambda: messagebox.showerror("Erro ADB", str(e)))
            self.ui(lambda: self.status_var.set(str(e)))
        except Exception as e:
            self.ui(lambda: messagebox.showerror("Erro", str(e)))
            self.ui(lambda: self.status_var.set(f"Erro: {e}"))

    def ui(self, func):
        self.after(0, func)

    def populate_table(self):
        selected_pkg = None
        sel = self.tree.selection()
        if sel:
            selected_pkg = self.tree.item(sel[0], "values")[2]
        self.tree.delete(*self.tree.get_children())
        filt = self.filter_var.get()
        for f in sorted(self.findings, key=lambda x: (x.score, x.package), reverse=True):
            if filt != "TODOS" and f.level != filt:
                continue
            self.tree.insert("", "end", values=(f.level, f.score, f.package, f.installer, "; ".join(f.reasons[:3])))
        if selected_pkg:
            for item in self.tree.get_children():
                if self.tree.item(item, "values")[2] == selected_pkg:
                    self.tree.selection_set(item)
                    break

    def get_selected_findings(self):
        selected = []
        pkgs = set()
        for item in self.tree.selection():
            values = self.tree.item(item, "values")
            if len(values) >= 3:
                pkgs.add(values[2])
        for f in self.findings:
            if f.package in pkgs:
                selected.append(f)
        return selected

    def on_select(self, event=None):
        findings = self.get_selected_findings()
        if not findings:
            self.details_set("")
            return
        f = findings[0]
        text = []
        text.append(f"Pacote: {f.package}")
        text.append(f"Risco: {f.level} ({f.score}/100)")
        text.append(f"Instalador: {f.installer}")
        text.append(f"APK: {f.apk_path}")
        text.append("Motivos:")
        for r in f.reasons or []:
            text.append(f"- {r}")
        if f.permissions:
            text.append("Permissões sensíveis encontradas: " + ", ".join(f.permissions))
        if f.appops:
            text.append("AppOps sensíveis: " + ", ".join(f.appops))
        self.details_set("\n".join(text))

    def details_set(self, text):
        self.details.configure(state="normal")
        self.details.delete("1.0", "end")
        self.details.insert("1.0", text)
        self.details.configure(state="disabled")


    def select_high_risk(self):
        self.tree.selection_remove(self.tree.selection())
        found = False
        for item in self.tree.get_children():
            values = self.tree.item(item, "values")
            if values and values[0] == "ALTO":
                self.tree.selection_add(item)
                found = True
        if found:
            self.status_var.set("Apps de alto risco selecionados. Revise a lista e clique em Remover selecionado(s).")
        else:
            messagebox.showinfo("Nenhum alto risco", "Nenhum app classificado como ALTO no filtro atual.")

    def copy_command_selected(self):
        findings = self.get_selected_findings()
        if not findings:
            messagebox.showinfo("Seleção vazia", "Selecione um app primeiro.")
            return
        commands = [f"adb shell pm uninstall --user 0 {f.package}" for f in findings]
        self.clipboard_clear()
        self.clipboard_append("\n".join(commands))
        self.status_var.set("Comando(s) copiado(s) para a área de transferência.")

    def uninstall_selected(self):
        findings = self.get_selected_findings()
        if not findings:
            messagebox.showinfo("Seleção vazia", "Selecione um ou mais apps primeiro.")
            return
        pkg_list = "\n".join(f"- {f.package} ({f.level}, {f.score})" for f in findings)
        ok = messagebox.askyesno(
            "Confirmar remoção",
            "Isso vai executar no celular:\n\npm uninstall --user 0 <pacote>\n\n"
            "Apps selecionados:\n" + pkg_list + "\n\nContinue apenas se você reconhece que o app é indesejado."
        )
        if not ok:
            return
        errors = []
        for f in findings:
            try:
                out = self.adb.uninstall_user0(self.device_serial, f.package)
                if "Success" not in out and "sucesso" not in out.lower():
                    errors.append(f"{f.package}: {out}")
            except Exception as e:
                errors.append(f"{f.package}: {e}")
        if errors:
            messagebox.showwarning("Remoção concluída com avisos", "\n".join(errors[:8]))
        else:
            messagebox.showinfo("Remoção concluída", "App(s) removido(s) para o usuário 0.")
        self.start_scan()

    def force_stop_selected(self):
        findings = self.get_selected_findings()
        if not findings:
            messagebox.showinfo("Seleção vazia", "Selecione um app primeiro.")
            return
        for f in findings:
            try:
                self.adb.force_stop(self.device_serial, f.package)
            except Exception:
                pass
        self.status_var.set("Force-stop enviado para o(s) app(s) selecionado(s).")

    def export_report(self):
        if not self.findings:
            messagebox.showinfo("Sem relatório", "Faça uma varredura antes de exportar.")
            return
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        default = f"relatorio_android_scanner_{stamp}.csv"
        path = filedialog.asksaveasfilename(
            title="Salvar relatório",
            defaultextension=".csv",
            initialfile=default,
            filetypes=[("CSV", "*.csv"), ("JSON", "*.json"), ("Texto", "*.txt")]
        )
        if not path:
            return
        p = Path(path)
        try:
            if p.suffix.lower() == ".json":
                with open(p, "w", encoding="utf-8") as f:
                    json.dump([fnd.to_row() for fnd in self.findings], f, ensure_ascii=False, indent=2)
            elif p.suffix.lower() == ".txt":
                with open(p, "w", encoding="utf-8") as f:
                    f.write(f"{APP_NAME} v{VERSION}\nGerado em: {datetime.now()}\n\n")
                    for fnd in sorted(self.findings, key=lambda x: x.score, reverse=True):
                        f.write(f"[{fnd.level} {fnd.score}/100] {fnd.package}\n")
                        f.write(f"Instalador: {fnd.installer}\n")
                        f.write("Motivos: " + "; ".join(fnd.reasons or []) + "\n")
                        f.write("Permissões: " + "; ".join(fnd.permissions or []) + "\n")
                        f.write("AppOps: " + "; ".join(fnd.appops or []) + "\n")
                        f.write("\n")
            else:
                with open(p, "w", newline="", encoding="utf-8-sig") as f:
                    fieldnames = ["nivel", "pontuacao", "pacote", "instalador", "motivos", "permissoes_sensiveis", "appops", "apk_path"]
                    writer = csv.DictWriter(f, fieldnames=fieldnames, delimiter=";")
                    writer.writeheader()
                    for fnd in sorted(self.findings, key=lambda x: x.score, reverse=True):
                        writer.writerow(fnd.to_row())
            messagebox.showinfo("Exportado", f"Relatório salvo em:\n{p}")
        except Exception as e:
            messagebox.showerror("Erro ao exportar", str(e))

    def open_rules(self):
        try:
            if os.name == "nt":
                os.startfile(str(RULES_PATH))
            elif sys.platform == "darwin":
                subprocess.run(["open", str(RULES_PATH)])
            else:
                subprocess.run(["xdg-open", str(RULES_PATH)])
        except Exception:
            messagebox.showinfo("rules.json", f"Arquivo de regras: {RULES_PATH}")


def main():
    app = ScannerApp()
    app.mainloop()


if __name__ == "__main__":
    main()
