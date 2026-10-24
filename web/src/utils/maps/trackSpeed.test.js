import test from 'node:test'
import assert from 'node:assert/strict'
import { coloredTrackSections, sectionSpeed, speedColor } from './trackSpeed.js'

test('0 to 400 km/h uses blue, bright green, yellow and red with clamped endpoints', () => {
  assert.equal(speedColor(0), '#268bff')
  assert.equal(speedColor(400 / 3), '#48e890')
  assert.equal(speedColor(800 / 3), '#ffe15a')
  assert.equal(speedColor(200), '#a4e575')
  assert.equal(speedColor(400), '#ff4438')
  assert.equal(speedColor(-10), speedColor(0))
  assert.equal(speedColor(900), speedColor(400))
  assert.equal(speedColor(null), '#8795a8')
})

test('speeds use milliseconds and geographic distance, without inventing speeds for invalid clocks', () => {
  const speed = sectionSpeed([0, 0, 1000], [0, 0.001, 11000])
  assert.ok(Math.abs(speed - 40.03) < 0.1)
  assert.equal(sectionSpeed([0, 0, 1000], [0, 0, 2000]), 0)
  assert.equal(sectionSpeed([0, 0, 1000], [0, 0.001, 1000]), null)
  assert.equal(sectionSpeed([0, 0], [0, 0.001]), null)
})

test('colored sections never join pauses and merge adjacent equal-speed sections', () => {
  const sections = coloredTrackSections([
    [[0, 0, 0], [0, 0.001, 10000], [0, 0.002, 20000]],
    [[1, 1, 30000], [1, 1.001, 40000]],
  ])
  assert.equal(sections.length, 2)
  assert.equal(sections[0].points.length, 3)
  assert.deepEqual(sections[1].points[0], [1, 1])
})
