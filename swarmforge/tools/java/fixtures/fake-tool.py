#!/usr/bin/python3
"""Controllable external Java tools for public CLI contracts."""
import json, os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
state_file = pathlib.Path(os.environ['SFJAVA_FAKE_STATE'])
s = json.loads(state_file.read_text())
args = sys.argv[1:]
with open(s['log'], 'a') as log:
    log.write(json.dumps({'tool': name, 'args': args, 'cwd': os.getcwd()})+'\n')
def write(file, text='fixture'):
    p=pathlib.Path(file); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text)
def finish(status=0): sys.exit(status)
if name == 'git':
    if 'fetch' in args:
        args=[s['upstreams'].get(a.rsplit('/',1)[-1].removesuffix('.git'), a) if a.startswith('https://github.com/') else a for a in args]
    os.execv(s['real_git'], [s['real_git']]+args)
if name == 'mvn':
    if '-version' in args: print('Apache Maven fixture'); finish()
    if not any(a in args for a in ['-DskipTests=true','-Dmaven.test.skip=true']):
        print('Maven test execution was not disabled',file=sys.stderr); finish(9)
    if 'test-compile' in args:
        if s.get('compile_failure'): print('compilation failed'); finish(1)
        write('target/sf-java/classpath.txt','')
        pathlib.Path('target/test-classes').mkdir(parents=True, exist_ok=True)
    if 'package' in args:
        tool=pathlib.Path.cwd().name
        write('target/'+tool+'-0.1.0-SNAPSHOT.jar')
    artifact=next((a.split('=',1)[1] for a in args if a.startswith('-Dartifact=')), None)
    if artifact:
        group, artifact_id, version, *rest=artifact.split(':')
        classifier=('-'+rest[1]) if len(rest)>1 else ''
        out=next(a.split('=',1)[1] for a in args if a.startswith('-DoutputDirectory='))
        write(str(pathlib.Path(out)/(artifact_id+'-'+version+classifier+'.jar')))
    finish()
if name == 'java':
    if args in [['-version'],['--version']]:
        print('openjdk version "'+('11.0.1' if s.get('old_java') else '17.0.12')+'"'); finish()
    jar=next((args[i+1] for i,a in enumerate(args) if a=='-jar'), '')
    if 'console-standalone' in jar:
        selected='property' if '--include-tag' in args else 'ordinary'
        print('RUN '+selected+' tests')
        if selected=='ordinary' and s.get('test_failure'): print('LedgerTest failed'); finish(1)
        if any(a.startswith('-javaagent:') for a in args) and not s.get('no_coverage'):
            write('target/jacoco.exec')
        finish()
    if 'org.jacoco.cli' in jar:
        if not s.get('no_coverage'):
            write('target/site/jacoco/jacoco.xml','<report><package name="demo"><class name="demo/Ledger"><counter type="INSTRUCTION" missed="0" covered="3"/></class></package></report>')
        finish()
    if '--help' in args:
        print('Java tool help'); finish()
    if 'crap4java-' in jar:
        if s.get('tool_failure'): print('analysis failure'); finish(1)
        import subprocess
        r=subprocess.run(['/bin/sh','-c',os.environ['SFJAVA_COVERAGE_COMMAND']])
        if r.returncode: finish(1)
        if not pathlib.Path('target/site/jacoco/jacoco.xml').exists(): finish(1)
        score=s.get('crap',1.0)
        print('CRAP Report\nMethod Class CC Cov% CRAP\nledger demo.Ledger 3 100.0% '+str(score))
        finish(2 if score>10 else 0)
    if 'dry4java-' in jar:
        if s.get('tool_failure'): print('malformed'); finish()
        def side(which):
            changed=s.get(which)=='changed'
            return '{:file "src/main/java/demo/'+('Ledger' if changed else 'Other')+'.java" :start-line 1 :end-line 8}'
        candidates='[{:score 0.9 :left '+side('left')+' :right '+side('right')+'}]' if 'left' in s else '[]'
        print('{:candidates '+candidates+'}'); finish()
    if 'mutate4java-' in jar:
        if s.get('baseline_failure'): print('Baseline tests failed.'); finish(2)
        import subprocess
        command=args[args.index('--test-command')+1]
        r=subprocess.run(['/bin/sh','-c',command])
        if r.returncode: finish(2)
        # Model one isolated mutant recompilation through the public runner.
        r=subprocess.run(['/bin/sh','-c',command])
        if r.returncode: finish(2)
        survivors=s.get('survivors',0)
        if not survivors and os.environ.get('SFJAVA_MANIFEST'): write(os.environ['SFJAVA_MANIFEST'],'tool-maintained-manifest')
        print('Summary: '+str(1-survivors)+' killed, '+str(survivors)+' survived, 1 total.')
        finish(3 if survivors else 0)
    print('fixture jar '+jar); finish()
print('unknown fake tool',name,file=sys.stderr); finish(9)
