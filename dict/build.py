import gzip,re,unicodedata,datetime
from wordfreq import top_n_list
today=datetime.date.today().isoformat()
HAN=re.compile(r'^[\u3400-\u9fff]{2,5}$')
src='/home/claude/dl/x/pycccedict/data/cedict_1_0_ts_utf-8_mdbg.txt.gz'
common=set();proper={}
for l in gzip.open(src,'rt',encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    m=re.match(r'^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$',l)
    if not m: continue
    trad,simp,py,defs=m.groups()
    forms={f for f in (simp,trad) if HAN.match(f)}
    if not forms: continue
    isProper=py[:1].isupper()
    if not isProper:
        common|=forms
    else:
        d=defs.split('/')[0].strip()
        d=re.sub(r'\s*\(.*?\)','',d)
        d=re.split(r'[,;]',d)[0].strip() if len(d)>40 else d
        d=re.sub(r'\s*\|.*$','',d)[:48].strip()
        if d.lower().startswith(('see ','variant of','abbr','old variant','also written','erhua')): d=''
        for f in forms:
            if f not in proper: proper[f]=d
# a word that is common in any reading is common
for w in list(proper):
    if w in common: del proper[w]
def hdr(lang,name,n,lic,srcline):
    return ['#JABRA-DICT 1 %s %s %d'%(lang,today,n),'# '+name,'# '+srcline,'# License: '+lic,'# Format: one word per line. A TAB and an English gloss after the word = proper noun (name, place, mythology). No TAB = ordinary word.']
lines=hdr('zh','Chinese common words and proper nouns from CC-CEDICT (MDBG copy)',len(common)+len(proper),
 'Creative Commons Attribution-ShareAlike 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)','Source: CC-CEDICT, https://cc-cedict.org/ . Changed: reduced to words of 2-5 characters, glosses shortened.')
lines+=sorted(common)
lines+=[w+'\t'+g for w,g in sorted(proper.items())]
open('jabra-dict-zh.txt','w',encoding='utf-8',newline='\n').write('\n'.join(lines)+'\n')
print('zh common',len(common),'proper',len(proper))
def wf(lang,rx,N):
    out=[]
    for w in top_n_list(lang,N):
        w=unicodedata.normalize('NFKC',w)
        if rx.match(w) and w not in out: out.append(w)
    return sorted(set(out))
ja=wf('ja',re.compile(r'^(?=.*[\u3400-\u9fff\u30a1-\u30fa])[\u3040-\u30ff\u3400-\u9fff\u30fc]{2,8}$'),40000)
ko=wf('ko',re.compile(r'^[\uac00-\ud7af]{2,6}$'),40000)
for lang,name,ws in (('ja','Japanese common words (top of the wordfreq list)',ja),('ko','Korean common words (top of the wordfreq list)',ko)):
    L=hdr(lang,name,len(ws),'Creative Commons Attribution-ShareAlike 4.0 (word list data of wordfreq)','Source: wordfreq by Robyn Speer, https://github.com/rspeer/wordfreq . Changed: top words only, filtered to the script.')
    open('jabra-dict-%s.txt'%lang,'w',encoding='utf-8',newline='\n').write('\n'.join(L+ws)+'\n')
    print(lang,len(ws))
