Jabra dictionary files
======================
jabra-dict-zh.txt  Chinese   CC-CEDICT (CC BY-SA 4.0), reduced to words of 2-5 characters
jabra-dict-ja.txt  Japanese  common words from wordfreq (CC BY-SA 4.0)
jabra-dict-ko.txt  Korean    common words from wordfreq (CC BY-SA 4.0)

Put these three files in a folder of your PUBLIC GitHub repository (for example dict/).
In the plugin: Glossary > Find new terms (free) > Offline dictionary > paste the folder link,
e.g. https://raw.githubusercontent.com/YOURNAME/YOURREPO/main/dict/  > Download.

Or save the files on the phone and use "Pick file".

Format: first line "#JABRA-DICT 1 <lang> <date> <count>", then one word per line.
A TAB and an English gloss after the word = proper noun. build.py rebuilds the files.
