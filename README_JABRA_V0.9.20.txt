Jabra Extension Hub v0.9.20 - FIX: validator emoji check

CAUSE: v0.9.19 CI failed. The new emoji check found emoji and symbols in res/layout/activity_main.xml (the nav icons, the filter arrow and the empty-state puzzle piece). That file was not in the list of files still to be cleaned, because it had not been seen yet.
FIX: scripts/validate_source.py: activity_main.xml added to EMOJI_PENDING (cleaned in step H1, which rebuilds this screen). No app code or layout changed.
In the real repo this was the only error (1 of 168 checks), so the validator should pass now.
Pending list (remove each when clean): Floater.kt (H5), PluginStore.kt (H2), MainActivity.kt (H1/H2), activity_main.xml (H1).

Version 0.9.20 (code 40). Changed files: scripts/validate_source.py, build.gradle.kts, this file.
Checked: validator run on a copy of the project with the real MainActivity, PluginActivity, PluginStore, BubbleService and activity_main.xml added: passes. Not built.
Please check: the validator step and the Gradle build both pass in CI (the build also confirms the ShapeAppearance.Q fix).
