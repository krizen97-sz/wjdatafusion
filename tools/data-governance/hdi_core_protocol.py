"""Validation and private result boundaries for the original-Core process worker."""
from __future__ import annotations

import base64
import hashlib
import ipaddress
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
    'fileInput': ('input', 'com.hikvision.idatafusion.etl.adapter.input.file.FileInput'),
    'postgresqlInput': ('input', 'com.hikvision.idatafusion.etl.adapter.input.postgresql.PostgresqlInput'),
    'postgresqlOutput': ('output', 'com.hikvision.idatafusion.etl.adapter.output.postgresql.PostgreSqlOutput'),
    'kafkaInput': ('input', 'com.hikvision.idatafusion.etl.adapter.input.kafka.KafkaInput'),
    'kafkaV2Output': ('output', 'com.hikvision.idatafusion.etl.adapter.output.kafkaV2.KafkaV2Output'),
    'rabbitmqInput': ('input', 'com.hikvision.idatafusion.etl.adapter.input.rabbitmq.RabbitmqInput'),
    'rabbitmqOutput': ('output', 'com.hikvision.idatafusion.etl.adapter.output.rabbitmq.RabbitmqOutput'),
    'mysqlInput': ('input', 'com.rynew.hdi.adapter.MysqlInput'),
    'mysqlOutput': ('output', 'com.rynew.hdi.adapter.MysqlOutput'),
}
TERMINAL = {'SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED', 'TIMED_OUT'}


class ProtocolError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def connector_address(value):
    """No DNS or wildcard target is accepted in the frozen connection grant."""
    try:
        address = ipaddress.IPv4Address(value)
    except (ValueError, ipaddress.AddressValueError):
        raise ProtocolError('CONNECTOR_ADDRESS_REQUIRED') from None
    if (not isinstance(value, str) or str(address) != value or address.is_unspecified
            or address.is_multicast or address.is_link_local or address.is_reserved
            or value.startswith('0.')):
        raise ProtocolError('CONNECTOR_ADDRESS_REQUIRED')
    return value


def connector_port(value):
    if type(value) is not int or not 1 <= value <= 65535 or value in (2180, 2181, 2182, 2183):
        raise ProtocolError('CONNECTOR_PORT_REJECTED')
    return value


def connection_bindings(value, owner):
    """Validate platform-owned grants separately from untrusted visual parameters."""
    if not isinstance(value, list) or len(value) > 16:
        raise ProtocolError('CONNECTOR_BINDING_LIMIT')
    result, ids = [], set()
    for binding in value:
        required = {'connectionId', 'connectionRevision', 'ownerId', 'kind', 'endpoints'}
        if not isinstance(binding, dict) or not required <= binding.keys() or binding.keys() - required - {'passivePorts'}:
            raise ProtocolError('INVALID_CONNECTOR_BINDING')
        key = positive_id(binding['connectionId'])
        if key in ids or positive_id(binding['ownerId']) != owner:
            raise ProtocolError('CONNECTOR_OWNER_MISMATCH')
        ids.add(key)
        kind = binding['kind']
        if kind not in ('postgresql', 'ftp', 'kafka', 'mysql', 'rabbitmq', 'sftp'):
            raise ProtocolError('CONNECTOR_NOT_SUPPORTED')
        endpoints = binding['endpoints']
        if not isinstance(endpoints, list) or not 1 <= len(endpoints) <= (16 if kind in ('kafka', 'rabbitmq') else 1):
            raise ProtocolError('CONNECTOR_ENDPOINT_LIMIT')
        normalized, seen = [], set()
        for endpoint in endpoints:
            if not isinstance(endpoint, dict) or set(endpoint) != {'host', 'address', 'port'}:
                raise ProtocolError('INVALID_CONNECTOR_ENDPOINT')
            address = connector_address(endpoint['address'])
            if endpoint['host'] != address:
                raise ProtocolError('CONNECTOR_ADDRESS_REQUIRED')
            port = connector_port(endpoint['port'])
            if (address, port) in seen:
                raise ProtocolError('DUPLICATE_CONNECTOR_ENDPOINT')
            seen.add((address, port))
            normalized.append({'host': address, 'address': address, 'port': port})
        item = {'connectionId': key, 'connectionRevision': positive_id(binding['connectionRevision']),
                'ownerId': owner, 'kind': kind, 'endpoints': normalized}
        passive = binding.get('passivePorts')
        if kind == 'ftp':
            if not isinstance(passive, dict) or set(passive) != {'start', 'end'}:
                raise ProtocolError('FTP_PASSIVE_RANGE_REQUIRED')
            start, end = connector_port(passive['start']), connector_port(passive['end'])
            if start < 1024 or not 0 <= end - start < 128 or any(start <= p <= end for p in (2180, 2181, 2182, 2183)):
                raise ProtocolError('FTP_PASSIVE_RANGE_REJECTED')
            item['passivePorts'] = {'start': start, 'end': end}
        elif passive is not None:
            raise ProtocolError('INVALID_CONNECTOR_BINDING')
        result.append(item)
    if len(connector_targets(result)) > 160:
        raise ProtocolError('CONNECTOR_ENDPOINT_LIMIT')
    return result


def connector_targets(bindings):
    """Fixed byte destinations only; passive and advertised addresses are explicit."""
    targets = set()
    for binding in bindings:
        for endpoint in binding['endpoints']:
            targets.add((endpoint['address'], endpoint['port']))
        if binding['kind'] == 'ftp':
            passive = binding['passivePorts']
            targets.update((binding['endpoints'][0]['address'], port)
                           for port in range(passive['start'], passive['end'] + 1))
    return [{'address': address, 'port': port} for address, port in sorted(targets)]


def redaction_values(value):
    if not isinstance(value, list) or len(value) > 64:
        raise ProtocolError('CONNECTOR_SECRET_LIMIT')
    if any(not isinstance(item, str) or not item or len(item) > 4096 or '\x00' in item for item in value):
        raise ProtocolError('INVALID_CONNECTOR_SECRET')
    return sorted(set(value), key=lambda item: (-len(item), item))


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
    if 'connectionBindings' in request:
        result['connectionBindings'] = connection_bindings(request['connectionBindings'], result['ownerId'])
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


def adapter_parameters(module):
    parameters = module.get('config', {}).get('adapter_param', [])
    if not isinstance(parameters, list):
        raise ProtocolError('INVALID_ADAPTER_PARAMETER')
    result = {}
    for parameter in parameters:
        if not isinstance(parameter, dict) or not isinstance(parameter.get('param_name'), str):
            raise ProtocolError('INVALID_ADAPTER_PARAMETER')
        key = parameter['param_name']
        if key in result:
            raise ProtocolError('DUPLICATE_ADAPTER_PARAMETER')
        result[key] = parameter.get('param_value')
    return result


def validate_datasource_grant(module, kind, bindings):
    """Check the generated destination against platform grants without rewriting it."""
    config = module.get('config', {})
    sources = config.get('datasource')
    if not isinstance(sources, list) or len(sources) != 1:
        raise ProtocolError('CONNECTOR_DATASOURCE_REQUIRED')
    if config.get('datasource_param', []) != []:
        raise ProtocolError('CONNECTOR_PARAMETERS_REJECTED')
    source = sources[0]
    if not isinstance(source, dict):
        raise ProtocolError('CONNECTOR_DATASOURCE_REQUIRED')
    key = native_id(source.get('id'))
    binding = next((item for item in bindings if item['connectionId'] == key and item['kind'] == kind), None)
    if binding is None:
        raise ProtocolError('CONNECTOR_BINDING_REQUIRED')
    endpoint = binding['endpoints'][0]
    if (source.get('db_type') != {'postgresql': 16, 'ftp': 18, 'kafka': 8, 'mysql': 2, 'rabbitmq': 120, 'sftp': 18}[kind]
            or source.get('db_ip') != endpoint['host'] or str(source.get('db_port')) != str(endpoint['port'])):
        raise ProtocolError('CONNECTOR_ENDPOINT_MISMATCH')
    if kind in ('postgresql', 'mysql'):
        database = source.get('db_sid')
        if not isinstance(database, str) or not re.fullmatch(r'[A-Za-z0-9_$.-]{1,128}', database):
            raise ProtocolError('CONNECTOR_DATABASE_REJECTED')
        expected = 'jdbc:' + kind + '://' + endpoint['host'] + ':' + str(endpoint['port']) + '/' + database
        if kind == 'mysql':
            expected += '?useSSL=false'  # Original MysqlDatasource fixed URL template.
    elif kind in ('ftp', 'sftp'):
        if source.get('ext2', 'ftp') != kind:
            raise ProtocolError('CONNECTOR_PROTOCOL_MISMATCH')
        expected = 'ftp://' + endpoint['host'] + ':' + str(endpoint['port'])
        if source.get('db_sid'):
            directory = source['db_sid']
            if not isinstance(directory, str) or len(directory) > 1024 or any(c in directory for c in ('\x00', '\r', '\n')):
                raise ProtocolError('CONNECTOR_DIRECTORY_REJECTED')
            # Preserve AbstractDatasource.jointUrl's original slash behavior.
            expected += '/' + directory
    else:
        url = source.get('db_url')
        prefix = 'kafka2://' if kind == 'kafka' else 'rabbitmq://'
        if not isinstance(url, str) or not url.startswith(prefix):
            raise ProtocolError('CONNECTOR_URL_MISMATCH')
        bootstrap = url[len(prefix):].split(',')
        allowed = {item['host'] + ':' + str(item['port']) for item in binding['endpoints']}
        if (not 1 <= len(bootstrap) <= 16 or len(set(bootstrap)) != len(bootstrap)
                or bootstrap[0] != endpoint['host'] + ':' + str(endpoint['port'])
                or not set(bootstrap) <= allowed):
            raise ProtocolError('CONNECTOR_URL_MISMATCH')
        # Advertised brokers may be authorized without being bootstrap seeds.
        # Keep the generator's original ordered bootstrap subset unchanged.
        expected = url
    if source.get('db_url') != expected:
        raise ProtocolError('CONNECTOR_URL_MISMATCH')
    if kind == 'kafka':
        params = adapter_parameters(module)
        if any(key in ('etl.kafka.bootstrap.servers', 'etl.kafka.key.deserializer', 'etl.kafka.value.deserializer',
                       'etl.kafka.key.serializer', 'etl.kafka.value.serializer')
               or key.startswith(('etl.kafka.sasl.', 'etl.kafka.ssl.', 'etl.kafka.security.')) for key in params):
            raise ProtocolError('CONNECTOR_PARAMETERS_REJECTED')
    if kind == 'rabbitmq':
        if str(source.get('ext1')) != '0' or adapter_parameters(module).get('rabbitmq.protocol.type', 'amqp') != 'amqp':
            raise ProtocolError('RABBITMQ_AMQP_PLAINTEXT_REQUIRED')
    return binding


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
    reserved_connections = record.get('connectionBindings', [])
    if connection_bindings(snapshot.get('connectionBindings', []), record['ownerId']) != reserved_connections:
        raise ProtocolError('COMPILED_CONNECTION_MISMATCH')
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
        if name == 'fileOutput':
            reject_filename_keys(module.get('config', {}))
        params = adapter_parameters(module)
        if ADAPTERS[name][0] == 'output':
            output_count += 1
        if name.startswith('postgresql'):
            validate_datasource_grant(module, 'postgresql', reserved_connections)
        elif name.startswith('mysql'):
            validate_datasource_grant(module, 'mysql', reserved_connections)
        elif name.startswith('rabbitmq'):
            validate_datasource_grant(module, 'rabbitmq', reserved_connections)
        elif name.startswith('kafka'):
            validate_datasource_grant(module, 'kafka', reserved_connections)
        elif name == 'fileInput':
            protocol = str(params.get('etl.file.server_type')).lower()
            if protocol not in ('ftp', 'sftp') or params.get('etl.file.server_download') != 'true':
                raise ProtocolError('REMOTE_FILE_INPUT_REQUIRED')
            validate_datasource_grant(module, protocol, reserved_connections)
            if protocol == 'ftp' and str(params.get('etl.file.server_passive_mode', '1')) != '1':
                raise ProtocolError('FTP_PASSIVE_REQUIRED')
            scratch = Path(record['outputsDir']).parent / 'tmp'
            below_output(params.get('etl.file.local_dir'), scratch)
            if params.get('etl.file.backDir'):
                below_output(params['etl.file.backDir'], scratch)
        elif name == 'fileOutput':
            below_output(params.get('etl.file.localDir'), record['outputsDir'])
            if params.get('etl.file.serverType') == 'local' and params.get('etl.file.isUpload') == 'false':
                below_output(params.get('etl.file.serverDir'), record['outputsDir'])
            elif str(params.get('etl.file.serverType')).lower() in ('ftp', 'sftp') and params.get('etl.file.isUpload') == 'true':
                protocol = str(params['etl.file.serverType']).lower()
                validate_datasource_grant(module, protocol, reserved_connections)
                if protocol == 'ftp' and str(params.get('etl.file.ftpPassiveMode', '1')) != '1':
                    raise ProtocolError('FTP_PASSIVE_REQUIRED')
            else:
                raise ProtocolError('LOCAL_OUTPUT_REQUIRED')
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
