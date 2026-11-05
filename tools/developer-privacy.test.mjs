import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const hiddenKeys = [
  'settings_developer', 'debug_tickets_title', 'failure_debug', 'failure_test_main',
  'failure_test_worker', 'failure_test_anr', 'network_features_enabled',
  'llt_auto_update', 'llt_import_title', 'developer_show_non_running_trains',
]
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
function files(directory, extension) {
  return fs.readdirSync(path.join(root, directory), { withFileTypes: true }).flatMap(entry => {
    const relative = path.join(directory, entry.name)
    return entry.isDirectory() ? files(relative, extension)
      : entry.name.endsWith(extension) ? [relative] : []
  })
}

test('project documentation and changelogs do not describe developer settings', () => {
  const values = read('app/src/ci/res/values/strings.xml')
  const labels = hiddenKeys.map(key => {
    const match = values.match(new RegExp(`<string name="${key}">([^<]+)</string>`))
    assert.ok(match, `Missing resource ${key}`)
    return match[1]
  }).filter(label => /[A-Za-z]/.test(label) ? label.length >= 10 : label.length >= 4)
  const documents = [
    ...files('docs', '.md'), ...files('changelogs', '.md'),
    ...fs.readdirSync(root).filter(name => /^readme.*\.md$/i.test(name)),
  ]
  for (const file of documents) {
    const content = read(file)
    for (const token of [
      ...labels, ...hiddenKeys, '/settings/developer', '开发者选项',
      '导入路路通数据库', '自动更新路路通数据', '显示当日不开行车次',
      '使用联网功能', 'G8964', 'CR400AF-T-2808',
    ]) {
      assert.ok(!content.includes(token), `${file} exposes ${token}`)
    }
  }
})

test('ordinary settings search and help do not index developer resources', () => {
  const sources = [
    'app/src/main/java/cc/samlab/ticket/ui/search/SearchSettings.kt',
    'app/src/main/java/cc/samlab/ticket/ui/search/SearchDestinations.kt',
    ...files('app/src/main/java/cc/samlab/ticket/ui/help', '.kt'),
  ]
  for (const source of sources) {
    for (const key of hiddenKeys) {
      assert.ok(!read(source).includes(`R.string.${key}`), `${source} indexes ${key}`)
    }
  }
  const secret = read('app/src/ci/java/cc/samlab/ticket/ui/settings/DeveloperOptionsState.kt')
  assert.ok(secret.includes('query == "G8964" || query == "CR400AF-T-2808"'))
})

test('public Android sources contain no developer routes, secrets or resources', () => {
  const sources = ['app/src/main', 'app/src/nonCi'].flatMap(directory => [
    ...files(directory, '.kt'), ...files(directory, '.xml'),
  ])
  const tokens = [
    ...hiddenKeys, '/settings/developer', 'developer-options',
    'CRTicketDeveloper', 'G8964', 'CR400AF-T-2808',
  ]
  for (const source of sources) {
    const content = read(source)
    for (const token of tokens) {
      assert.ok(!content.includes(token), `${source} includes ${token}`)
    }
  }
})
