import json
import struct
import sys
import zipfile

TOKENS = [
    'CRTicketDeveloper', 'G8964', 'CR400AF-T-2808', '/settings/developer',
    'developer-options', 'crticketDebugGenerate', 'crticketTimetable=',
    'DeveloperOptionsState', 'DebugTicketsController', 'TimetableSettingsController',
    'CiSettingsBridge', 'CI simulated main thread crash',
    'settings_developer', 'developer_show_non_running_trains', 'llt_import_title',
    'network_features_enabled', 'debug_tickets_title', 'failure_test_main',
    '/ticket/add-batch', 'importArchive',
]

def dex_strings(data):
    size, offset = struct.unpack_from('<II', data, 56)
    result = []
    for i in range(size):
        position = struct.unpack_from('<I', data, offset + i * 4)[0]
        while data[position] & 128:
            position += 1
        position += 1
        end = data.index(b'\0', position)
        result.append(data[position:end].decode('utf-8', errors='replace'))
    return '\n'.join(result)

reports = []
for filename in sys.argv[1:]:
    ci = 'ciOffline' in filename or '/ci/' in filename.replace('\\', '/')
    hits = {}
    with zipfile.ZipFile(filename) as apk:
        for name in apk.namelist():
            if name.endswith('.dex'):
                content = dex_strings(apk.read(name))
            elif name.endswith(('.js', '.html')) or name == 'resources.arsc':
                data = apk.read(name)
                content = data.decode('utf-8', errors='replace') + data.decode('utf-16le', errors='replace')
            else:
                continue
            found = [token for token in TOKENS if token in content]
            if found:
                hits[name] = found
    reports.append({'apk': filename, 'ci': ci, 'hits': hits})
    assert bool(hits) == ci, f'Unexpected CI content in {filename}: {hits}'
print(json.dumps(reports, indent=2, ensure_ascii=False))
