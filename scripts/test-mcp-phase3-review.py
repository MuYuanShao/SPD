"""Provision a disposable MySQL instance for phase-three review regressions; never use a business database."""
import datetime
import argparse
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
MYSQL = Path(os.environ.get("SPD_TEST_MYSQL_BIN", "C:/Program Files/MySQL/MySQL Server 8.4/bin"))
PORT = 13327
RUN = ROOT / "output" / "phase3-review-tests" / ("phase3-review-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tests', default='McpPhase3ReviewMysqlTest')
    args = parser.parse_args()
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    RUN.mkdir(parents=True)
    data = RUN / "mysql-data"
    password = secrets.token_hex(24)
    env = os.environ.copy()
    log = open(RUN / "mysql.log", "wb")
    server = None
    def sql(statement, authenticated=True):
        sql_env = env.copy()
        sql_env.pop("MYSQL_PWD", None)
        if authenticated:
            sql_env["MYSQL_PWD"] = password
        result = subprocess.run([str(MYSQL / "mysql.exe"), "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", f"--port={PORT}", "--user=root"], input=statement.encode(), capture_output=True, env=sql_env, creationflags=FLAGS)
        if result.returncode:
            raise RuntimeError("Owned MySQL operation failed")
    try:
        subprocess.run([str(MYSQL / "mysqld.exe"), "--no-defaults", "--initialize-insecure", f"--datadir={data}"], stdout=log, stderr=log, creationflags=FLAGS, check=True)
        server = subprocess.Popen([str(MYSQL / "mysqld.exe"), "--no-defaults", f"--datadir={data}", "--bind-address=127.0.0.1", f"--port={PORT}", "--mysqlx=0", "--innodb-buffer-pool-size=64M"], stdout=log, stderr=log, creationflags=FLAGS)
        for attempt in range(60):
            try:
                sql("SELECT 1", False)
                break
            except RuntimeError:
                if attempt == 59:
                    raise
                time.sleep(1)
        sql(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{password}'; CREATE DATABASE spd_phase3_review CHARACTER SET utf8mb4;", False)
        env.update({"SPD_PHASE3_REVIEW_TESTS": "true", "SPD_PHASE3_REVIEW_URL": f"jdbc:mysql://127.0.0.1:{PORT}/spd_phase3_review?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", "SPD_PHASE3_REVIEW_PASSWORD": password})
        candidates = list((Path.home() / ".m2/wrapper/dists/apache-maven-3.9.9").glob("*/bin/mvn.cmd"))
        maven = os.environ.get("SPD_TEST_MAVEN") or (str(candidates[0]) if candidates else shutil.which("mvn"))
        with open(RUN / "tests.log", "wb") as tests:
            result = subprocess.run([maven, "-f", str(ROOT / "backend/pom.xml"), "-Dtest=" + args.tests, "test"], cwd=ROOT, env=env, stdout=tests, stderr=subprocess.STDOUT, creationflags=FLAGS)
        print("Review regression log: " + str(RUN / "tests.log"), flush=True)
        return result.returncode
    finally:
        if server is not None and server.poll() is None:
            shutdown_env = env.copy(); shutdown_env["MYSQL_PWD"] = password
            try:
                subprocess.run([str(MYSQL / "mysqladmin.exe"), "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", f"--port={PORT}", "--user=root", "shutdown"], env=shutdown_env, capture_output=True, creationflags=FLAGS, timeout=15)
                server.wait(timeout=20)
            except (OSError, subprocess.TimeoutExpired):
                if server.poll() is None:
                    server.terminate()
                    server.wait(timeout=10)
        log.close()

if __name__ == "__main__":
    raise SystemExit(main())
