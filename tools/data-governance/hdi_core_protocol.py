"""Validation and private result boundaries for the original-Core process worker."""
from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import uuid

MAX_NATIVE_BYTES = 4 * 1024 * 1024
MAX_LONG = (1 << 63) - 1
ADAPTERS = {
    'dataGeneratorInput': ('input', 'com.hikvision.idatafusion.adapter.input.data.DataGeneratorInput'),
    'fieldMapping': ('preproc', 'com.hikvision.idatafusion.etl.adapter.process.fieldmapping.FieldMapping'),
    'fileOutput': ('output', 'com.hikvision.idatafusion.etl.adapter.output.file.FileOutput'),
}
TERMINAL = {'SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED', 'TIMED_OUT'}


class ProtocolError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def positive_id(value, maximum=MAX_LONG):
    if not isinstance(value, str) or not re.fullmatch(r'[1-9][0-9]{0,18}', value) or int(value) > maximum:
        raise ProtocolError('INVALID_ID')
    return value


def native_id(value, maximum=MAX_LONG):
    if isinstance(value, bool) or not isinstance(value, (int, str)):
        raise ProtocolError('INVALID_NATIVE_ID')
    return positive_id(str(value), maximum)


def run_id(value):
    try:
        if not isinstance(value, str) or str(uuid.UUID(value)) != value:
            raise ValueError()
    except (ValueError, AttributeError):
        raise ProtocolError('INVALID_RUN_ID') from None
    return value


def strict_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ProtocolError('DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    try:
        return json.loads(data, object_pairs_hook=pairs,
                          parse_constant=lambda _: (_ for _ in ()).throw(ProtocolError('NONFINITE_JSON')))
    except (ValueError, UnicodeError):
        raise ProtocolError('INVALID_JSON') from None


def atomic_bytes(path, data):
    path = Path(path)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.state-', delete=False) as out:
            temporary = Path(out.name)
            os.fchmod(out.fileno(), 0o600)
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
        fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def atomic_json(path, value):
    data = json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
    atomic_bytes(path, data)


def private_file(path, maximum=16 * 1024 * 1024):
    path = Path(path)
    flags = os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(path, flags)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
                or info.st_mode & 0o077 or info.st_size > maximum):
            raise ProtocolError('PRIVATE_FILE_REQUIRED')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            data = stream.read(maximum + 1)
        if len(data) > maximum:
            raise ProtocolError('PRIVATE_FILE_LIMIT')
        return data
    finally:
        os.close(fd)


def reservation(request):
    result = {'runId': run_id(request.get('runId')), 'ownerId': positive_id(request.get('ownerId'))}
    task = request.get('businessTaskId')
    if not isinstance(task, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', task):
        raise ProtocolError('INVALID_TASK_ID')
    revision = positive_id(request.get('revision'))
    result.update(businessTaskId=task, revision=revision)
    for key in ('nativeFlowId', 'nativeInstanceId', 'execId', 'metadataIdBase'):
        result[key] = positive_id(request.get(key))
    result['workspaceId'] = positive_id(request.get('workspaceId'), (1 << 31) - 1)
    if len({result['nativeFlowId'], result['nativeInstanceId'], result['execId']}) != 3:
        raise ProtocolError('NATIVE_ID_COLLISION')
    low = int(result['metadataIdBase'])
    if low + 8191 > MAX_LONG or any(low <= int(result[k]) <= low + 8191
                                    for k in ('nativeFlowId', 'nativeInstanceId', 'execId')):
        raise ProtocolError('NATIVE_ID_COLLISION')
    if request.get('mode') not in ('test', 'run'):
        raise ProtocolError('INVALID_MODE')
    result['mode'] = request['mode']
    return result


def native_blob(value):
    if not isinstance(value, dict) or set(value) != {'base64', 'sha256'}:
        raise ProtocolError('INVALID_NATIVE_ENVELOPE')
    encoded, expected = value['base64'], value['sha256']
    if not isinstance(encoded, str) or len(encoded) > MAX_NATIVE_BYTES * 4 // 3 + 8:
        raise ProtocolError('NATIVE_BYTES_LIMIT')
    if not isinstance(expected, str) or not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ProtocolError('INVALID_NATIVE_HASH')
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, TypeError):
        raise ProtocolError('INVALID_NATIVE_BASE64') from None
    if not data or len(data) > MAX_NATIVE_BYTES or hashlib.sha256(data).hexdigest() != expected:
        raise ProtocolError('NATIVE_HASH_MISMATCH')
    decoded = strict_json(data)
    if not isinstance(decoded, dict):
        raise ProtocolError('NATIVE_OBJECT_REQUIRED')
    return data, decoded


def below_output(value, root):
    if not isinstance(value, str) or '\x00' in value or '\\' in value:
        raise ProtocolError('OUTPUT_PATH_REJECTED')
    path = Path(value)
    root = Path(root).resolve(strict=True)
    if not path.is_absolute() or '..' in path.parts:
        raise ProtocolError('OUTPUT_PATH_REJECTED')
    for part in (path, *path.parents):
        if part.is_symlink():
            raise ProtocolError('OUTPUT_PATH_REJECTED')
    resolved = path.resolve()
    if resolved != root and root not in resolved.parents:
        raise ProtocolError('OUTPUT_PATH_REJECTED')
    return resolved


def validate_compiled(compiled, record):
    if not isinstance(compiled, dict) or compiled.get('ok') is not True or compiled.get('protocolVersion') != 1:
        raise ProtocolError('INVALID_COMPILER_RESULT')
    for key, expected in [('businessTaskId', record['businessTaskId']), ('ownerId', record['ownerId']),
                          ('revision', record['revision']), ('flowId', record['nativeFlowId']),
                          ('workspaceId', record['workspaceId']), ('instanceId', record['nativeInstanceId']),
                          ('execId', record['execId'])]:
        if type(compiled.get(key)) is not type(expected) or compiled[key] != expected:
            raise ProtocolError('COMPILED_RESERVATION_MISMATCH')
    if not re.fullmatch(r'[0-9a-f]{64}', str(compiled.get('snapshotSha256', ''))):
        raise ProtocolError('SNAPSHOT_HASH_REQUIRED')
    blobs = {key: native_blob(compiled.get(key)) for key in ('flowjson', 'schedule', 'stop')}
    def no_type_directives(value):
        if isinstance(value, dict):
            if '@type' in value:
                raise ProtocolError('NATIVE_TYPE_DIRECTIVE_REJECTED')
            for child in value.values():
                no_type_directives(child)
        elif isinstance(value, list):
            for child in value:
                no_type_directives(child)
    for _, decoded in blobs.values():
        no_type_directives(decoded)
    snapshot = compiled.get('metadataSnapshot', {})
    if (not isinstance(snapshot, dict)
            or native_id(snapshot.get('metadataIdBase')) != record['metadataIdBase']
            or not int(record['metadataIdBase']) <= int(native_id(snapshot.get('allocatedThrough'))) <= int(record['metadataIdBase']) + 8191
            or snapshot.get('outputsDir') != record['outputsDir']):
        raise ProtocolError('COMPILED_METADATA_MISMATCH')
    graph, schedule, stop = (blobs[key][1] for key in ('flowjson', 'schedule', 'stop'))
    flow = graph.get('flow', {})
    if (native_id(flow.get('id')) != record['nativeFlowId']
            or native_id(flow.get('group_id'), (1 << 31) - 1) != record['workspaceId']):
        raise ProtocolError('FLOW_ID_MISMATCH')
    strategy = schedule.get('strategy_flow', {})
    if (native_id(schedule.get('id')) != record['nativeFlowId']
            or schedule.get('exec_id') != record['execId']
            or schedule.get('oper_type') != 1 or schedule.get('oper_value') != 1
            or native_id(strategy.get('id')) != record['nativeFlowId']
            or native_id(strategy.get('group_id'), (1 << 31) - 1) != record['workspaceId']):
        raise ProtocolError('SCHEDULE_ID_MISMATCH')
    if (native_id(stop.get('id')) != record['nativeFlowId']
            or stop.get('oper_type') != 1 or stop.get('oper_value') != 0):
        raise ProtocolError('STOP_ID_MISMATCH')
    streams = graph.get('streams')
    if not isinstance(streams, list) or not 1 <= len(streams) <= 32:
        raise ProtocolError('STREAM_LIMIT')
    modules, output_count = [], 0
    for stream in streams:
        if not isinstance(stream, dict) or not isinstance(stream.get('modules'), list):
            raise ProtocolError('INVALID_STREAM')
        modules.extend(stream['modules'])
    if not 1 <= len(modules) <= 128:
        raise ProtocolError('MODULE_LIMIT')
    for module in modules:
        info, adapter = module.get('module_info', {}), module.get('adapter', {})
        name = adapter.get('adapter_name')
        if (name not in ADAPTERS or adapter.get('adapter_type') != ADAPTERS[name][0]
                or adapter.get('adapter_class_name') != ADAPTERS[name][1]
                or adapter.get('language_type') != 1 or info.get('adapter_version') != '2.0.0'):
            raise ProtocolError('ADAPTER_NOT_SUPPORTED')
        if (native_id(info.get('flow_id')) != record['nativeFlowId']
                or native_id(info.get('module_instance_id')) != record['nativeInstanceId']):
            raise ProtocolError('MODULE_OWNER_MISMATCH')
        def reject_filename_keys(value):
            if isinstance(value, dict):
                if value.get('field_key_flag', 0) not in (None, 0, '0'):
                    raise ProtocolError('DYNAMIC_FILENAME_NOT_SUPPORTED')
                for child in value.values():
                    reject_filename_keys(child)
            elif isinstance(value, list):
                for child in value:
                    reject_filename_keys(child)
        reject_filename_keys(module.get('config', {}))
        if name == 'fileOutput':
            output_count += 1
            params = {}
            for parameter in module.get('config', {}).get('adapter_param', []):
                key = parameter.get('param_name')
                if key in params:
                    raise ProtocolError('DUPLICATE_ADAPTER_PARAMETER')
                params[key] = parameter.get('param_value')
            if params.get('etl.file.serverType') != 'local' or params.get('etl.file.isUpload') != 'false':
                raise ProtocolError('LOCAL_OUTPUT_REQUIRED')
            for key in ('etl.file.localDir', 'etl.file.serverDir'):
                below_output(params.get(key), record['outputsDir'])
    if not output_count:
        raise ProtocolError('OUTPUT_REQUIRED')
    return blobs


def results_manifest(root, max_files=128, max_bytes=64 * 1024 * 1024):
    root = Path(root)
    if any(path.is_symlink() for path in (root, *root.parents)):
        raise ProtocolError('OUTPUT_SYMLINK_REJECTED')
    root = root.resolve(strict=True)
    result, total = [], 0
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ProtocolError('OUTPUT_SYMLINK_REJECTED')
        if not path.is_file():
            if not path.is_dir():
                raise ProtocolError('OUTPUT_TYPE_REJECTED')
            continue
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            info = os.fstat(fd)
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
                raise ProtocolError('OUTPUT_TYPE_REJECTED')
            total += info.st_size
            if len(result) >= max_files or total > max_bytes:
                raise ProtocolError('OUTPUT_LIMIT')
            sha = hashlib.sha256()
            with os.fdopen(fd, 'rb', closefd=False) as stream:
                for block in iter(lambda: stream.read(65536), b''):
                    sha.update(block)
            after = os.fstat(fd)
            if (after.st_size, after.st_mtime_ns, after.st_ctime_ns) != (info.st_size, info.st_mtime_ns, info.st_ctime_ns):
                raise ProtocolError('OUTPUT_CHANGED')
            relative = str(path.relative_to(root))
            result.append({'artifactId': hashlib.sha256(relative.encode()).hexdigest()[:32],
                           'name': relative, 'bytes': info.st_size, 'sha256': sha.hexdigest(),
                           '_fileIdentity': [info.st_dev, info.st_ino, info.st_mtime_ns, info.st_ctime_ns]})
        finally:
            os.close(fd)
    return result
