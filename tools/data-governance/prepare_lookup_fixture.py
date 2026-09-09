#!/usr/bin/env python3
"""Create only the owned synthetic PostgreSQL dictionary used by local ETL acceptance."""
import argparse
import json
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, required=True)
    args = parser.parse_args()
    root = args.runtime.resolve()
    marker = json.loads((root / 'runtime.json').read_text())
    if marker.get('owner') != 'rynew-data-governance-runtime' or Path(marker['runtimeRoot']).resolve() != root:
        raise RuntimeError('Dedicated runtime ownership is required')
    path = root / 'private/postgres-credentials.json'
    if path.stat().st_mode & 0o077:
        raise RuntimeError('Private credentials permissions must be 600')
    credentials = json.loads(path.read_text())
    if credentials['host'] != '127.0.0.1' or credentials['port'] != 15432:
        raise RuntimeError('Refusing a non-fixture PostgreSQL endpoint')
    env = {**os.environ, 'PGPASSWORD': credentials['password']}
    argv = ['/opt/homebrew/opt/postgresql@17/bin/psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
        '-h', credentials['host'], '-p', str(credentials['port']), '-U', credentials['username'], '-d', credentials['database']]
    def sql(value):
        result = subprocess.run(argv, input=value, text=True, capture_output=True, env=env)
        if result.returncode:
            raise RuntimeError('Owned PostgreSQL fixture command failed; credentials and SQL data are not printed')
        return result.stdout.strip()
    if Path(sql('SHOW data_directory;')).resolve() != (root / 'postgres/data').resolve():
        raise RuntimeError('PostgreSQL data directory ownership mismatch')
    sql("""
BEGIN;
DO $$
BEGIN
  IF to_regclass('public.governance_demo_camera') IS NOT NULL
    AND obj_description(to_regclass('public.governance_demo_camera'),'pg_class') IS DISTINCT FROM 'rynew-governance-lookup-fixture'
  THEN RAISE EXCEPTION 'Fixture table is not owned'; END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.governance_demo_camera (
  id integer PRIMARY KEY, camera_code text, platform_code text, external_code text, active text
);
COMMENT ON TABLE public.governance_demo_camera IS 'rynew-governance-lookup-fixture';
INSERT INTO public.governance_demo_camera VALUES
 (1,'CAM-001','DEMO','EXT-001','Y'),
 (2,'CAM-001','OTHER','OTHER-001','Y'),
 (3,'CAM-002','DEMO','EXT-002',NULL)
ON CONFLICT(id) DO NOTHING;
COMMIT;
""")
    print(json.dumps({'fixtureReady': True, 'table': 'public.governance_demo_camera',
        'columns': ['camera_code', 'platform_code', 'external_code', 'active'], 'rowCount': int(sql('SELECT count(*) FROM public.governance_demo_camera;'))}))


if __name__ == '__main__':
    main()
