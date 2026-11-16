"""Run the MySQL-only regression against a disposable server under target/.
Requires mysql and mysqld on PATH, or MYSQL_TEST_BIN pointing to their directory.
Retains test files for inspection; never connects to an existing server.
"""
import os, subprocess, pathlib, tempfile, socket, time, shutil, argparse, urllib.request, json
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--backend',action='store_true',help='Run Boot HTTP regressions and packaged JAR smoke test instead of legacy favorites')
args=parser.parse_args()
workspace=pathlib.Path(__file__).resolve().parent.parent
os.chdir(workspace)
base=workspace/'target'
base.mkdir(exist_ok=True)
os.environ.pop('MYSQL_PWD',None)
data=pathlib.Path(tempfile.mkdtemp(prefix='mysql-regression-',dir=base))
server_binary=shutil.which('mysqld')
if not os.environ.get('MYSQL_TEST_BIN') and not server_binary:
    raise SystemExit('Set MYSQL_TEST_BIN or add mysqld to PATH')
bin=pathlib.Path(os.environ['MYSQL_TEST_BIN']) if os.environ.get('MYSQL_TEST_BIN') else pathlib.Path(server_binary).parent
server_name='mysqld.exe' if os.name=='nt' else 'mysqld'
client_name='mysql.exe' if os.name=='nt' else 'mysql'
flags=getattr(subprocess,'CREATE_NO_WINDOW',0)
log=(data/'runner.log').open('w')
server=None
application=None
application_log=None
try:
    subprocess.run([str(bin/server_name),'--no-defaults','--initialize-insecure','--datadir='+str(data/'data')],stdout=log,stderr=log,check=True,creationflags=flags,timeout=60)
    with socket.socket() as s:
        s.bind(('127.0.0.1',0));port=s.getsockname()[1]
    server=subprocess.Popen([str(bin/server_name),'--no-defaults','--datadir='+str(data/'data'),'--bind-address=127.0.0.1','--port='+str(port),'--mysqlx=0','--skip-log-bin'],stdout=log,stderr=log,creationflags=flags)
    client=[str(bin/client_name),'--no-defaults','--skip-password','--connect-timeout=2','--protocol=TCP','--host=127.0.0.1','--port='+str(port),'--user=root']
    for _ in range(150):
        if server.poll() is not None: raise RuntimeError('isolated server exited')
        check=subprocess.run(client+['--execute=SELECT 1'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,creationflags=flags)
        if check.returncode==0:break
        time.sleep(.2)
    else: raise RuntimeError('isolated server startup timed out: '+check.stderr.decode(errors='replace'))
    subprocess.run(client+['--execute=CREATE DATABASE lvdong_ephemeral_regression'],check=True,stdout=log,stderr=log,creationflags=flags)
    env=os.environ.copy();env.pop('APP_CONFIG_FILE',None)
    env.update(DB_DRIVER='com.mysql.cj.jdbc.Driver',DB_URL='jdbc:mysql://127.0.0.1:'+str(port)+'/lvdong_ephemeral_regression?useSSL=false&allowPublicKeyRetrieval=true',DB_USERNAME='root',DB_PASSWORD='')
    env['LVDONG_ISOLATED_MYSQL']='true'
    with (data/'maven.log').open('w') as output:
        options=['-f','backend/pom.xml','-Pmysql-regression','-Dtest=SearchMySqlIntegrationTest','verify'] if args.backend else ['-Dtest=FavoriteMySqlIntegrationTest','test']
        command=(['cmd.exe','/d','/c'] if os.name=='nt' else [])+['mvn','-o','-q']+options
        result=subprocess.run(command,env=env,stdout=output,stderr=output,creationflags=flags,timeout=120)
    if result.returncode: raise RuntimeError('MySQL regression failed; inspect '+str(data/'maven.log'))
    if args.backend:
        with socket.socket() as s:
            s.bind(('127.0.0.1',0));application_port=s.getsockname()[1]
        env['PORT']=str(application_port)
        application_log=(data/'application.log').open('w')
        application=subprocess.Popen(['java','-jar',str(workspace/'backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar'),
            '--server.port='+str(application_port),'--spring.datasource.url='+env['DB_URL'],
            '--spring.datasource.username=root','--spring.datasource.password=',
            '--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver','--spring.sql.init.mode=never'],
            env=env,stdout=application_log,stderr=application_log,creationflags=flags)
        for _ in range(100):
            if application.poll() is not None: raise RuntimeError('Packaged backend exited; inspect application.log')
            try:
                with urllib.request.urlopen('http://127.0.0.1:'+str(application_port)+'/api/v1/search?keyword=missing',timeout=1) as response:
                    payload=json.load(response)
                if payload['code']!=0 or payload['data']['songs']!=[]: raise RuntimeError('Unexpected packaged search response')
                break
            except (OSError, urllib.error.URLError): time.sleep(.2)
        else: raise RuntimeError('Packaged backend startup timed out')
        print('PASS: disposable MySQL Boot HTTP regressions and packaged JAR startup/search')
    else:
        migration=(workspace/'src/main/resources/sql/upgrade/002_song_likes_unique.sql').read_bytes()
        for _ in range(2):
            subprocess.run(client+['lvdong_ephemeral_regression'],input=migration,stdout=log,stderr=log,check=True,creationflags=flags)
        index=subprocess.check_output(client+['--batch','--skip-column-names','lvdong_ephemeral_regression','--execute=SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=\'song_likes\' AND index_name=\'uk_song_likes_song_user\' AND non_unique=0'],stderr=log,creationflags=flags)
        if index.strip()!=b'2': raise RuntimeError('Unique migration index verification failed')
        print('PASS: disposable MySQL concurrent favorites, rollback and repeated incremental migration; data retained under target')
finally:
    if application is not None:
        application.terminate()
        application.wait(timeout=15)
    if application_log is not None: application_log.close()
    if server is not None:
        # mysqld on Windows may supervise a child; terminate() alone leaves that child running.
        admin_name='mysqladmin.exe' if os.name=='nt' else 'mysqladmin'
        shutdown=subprocess.run([str(bin/admin_name),'--no-defaults','--host=127.0.0.1','--port='+str(port),'--user=root','--skip-password','shutdown'],stdout=log,stderr=log,creationflags=flags,timeout=30)
        if shutdown.returncode and server.poll() is None:
            raise RuntimeError('Temporary MySQL did not shut down; inspect runner.log')
        try: server.wait(timeout=30)
        except subprocess.TimeoutExpired: raise RuntimeError('Temporary MySQL supervisor did not exit')
    log.close()
