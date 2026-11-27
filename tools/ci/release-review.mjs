import { readFileSync } from 'node:fs'

try {
  const history = JSON.parse(readFileSync(0, 'utf8'))
  const modes = new Set()
  for (const review of history) {
    if (review.state !== 'approved' ||
        !review.environments.some(environment => environment.name === 'release')) continue
    const comment = (review.comment ?? '').trim().toLowerCase()
    switch (comment) {
      case '':
      case 'release':
        modes.add('release')
        break
      case 'prerelease':
      case 'pre-release':
        modes.add('prerelease')
        break
      default:
        throw new Error('Use prerelease, release, or a blank deployment review comment.')
    }
  }
  if (modes.size === 0) throw new Error('No approval was recorded for the release environment.')
  if (modes.size > 1) throw new Error('Deployment reviews disagree on the release type.')
  console.log(modes.values().next().value)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
