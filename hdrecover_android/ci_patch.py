from pathlib import Path

path = Path("hdrecover_android/app/src/main/java/com/tpoll/hdrecover/RecoveryEngine.java")
text = path.read_text(encoding="utf-8")
unicode_null = "\\" + "u0000"
text = text.replace(
    'private static final byte[] SIG_SQLITE = "SQLite format 3' + unicode_null + '".getBytes(StandardCharsets.US_ASCII);',
    'private static final byte[] SIG_SQLITE = {0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66, 0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00};'
)
text = text.replace(
    '!asciiEquals(header, 0, "SQLite format 3' + unicode_null + '")',
    '!java.util.Arrays.equals(java.util.Arrays.copyOfRange(header, 0, SIG_SQLITE.length), SIG_SQLITE)'
)
path.write_text(text, encoding="utf-8")
