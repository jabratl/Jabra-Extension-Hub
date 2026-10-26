Jabra Extension Hub v0.8.0 - Chapter intake ("Jabra Scan")

NEW: send text to Jabra from other apps and it opens in Novel Assistant with the glossary scan ready.
 - Select text in any app (Chrome, Google Docs, readers) -> menu -> "Jabra Scan".
 - Share -> "Jabra Scan" from a browser (a chapter link is fetched and converted to text), or share plain text.
 - Open or share a .txt, .docx, .epub or .html file with "Jabra Scan".
 - New plugin API: JabraAPI.intakeGet() / intakeClear(), capability "chapterIntake".
 - Plugin screen is singleTop, so a second scan updates the open plugin instead of stacking screens.

Limits: Android cannot silently read other apps; the user must select, share or open. PDF is not supported yet.
Web pages that need login/JavaScript cannot be fetched; select the text instead.
Not built or device-tested in this package. Check the GitHub Actions result and test on the phone.
