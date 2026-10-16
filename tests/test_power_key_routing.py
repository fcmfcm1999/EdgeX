#!/usr/bin/env python3
"""Source-level checks connecting the Android hooks to the JVM-tested power detector."""
from pathlib import Path
import os
import unittest

ROOT = Path(os.environ.get('EDGEX_SOURCE_ROOT', Path(__file__).resolve().parents[1]))
HOOK = ROOT / 'app/src/main/java/com/fan/edgex/hook'


class PowerKeyRoutingTest(unittest.TestCase):
    def test_power_is_intercepted_before_system_policy(self):
        source = (HOOK / 'MainHook.kt').read_text()
        self.assertIn('inputManagerService, "interceptKeyBeforeQueueing"', source)
        self.assertIn('GestureManager.handlePowerKeyBeforeQueueing(keyEvent, context)', source)
        self.assertIn('param.result = 0', source)

    def test_power_does_not_enter_late_state_machine(self):
        source = (HOOK / 'KeyManager.kt').read_text()
        late = source.split('fun handleKeyEvent(', 1)[1].split('private fun handleKeyDown(', 1)[0]
        self.assertIn('if (keyCode == KeyEvent.KEYCODE_POWER) return false', late)

    def test_power_uses_tested_detector_and_injection_registry(self):
        source = (HOOK / 'KeyManager.kt').read_text()
        self.assertIn('PowerKeyGestureHandler<KeyEvent>', source)
        self.assertIn('InjectedKeyRegistry', source)
        self.assertIn('fun handlePowerKeyBeforeQueueing(', source)

    def test_replay_marker_matches_native_normalized_device_id(self):
        source = (HOOK / 'KeyManager.kt').read_text()
        self.assertIn('injectedKeys.mark(event.injectionIdentity().asInjected())', source)

    def test_physical_volume_chords_can_handoff_to_native_policy(self):
        source = (HOOK / 'KeyManager.kt').read_text()
        self.assertIn('powerKeyHandler?.handoffToNative()', source)
        self.assertIn('heldVolumeKeys.isEmpty()', source)
        source = (HOOK / 'MainHook.kt').read_text()
        self.assertIn('keyEvent.keyCode != KeyEvent.KEYCODE_VOLUME_UP', source)
        self.assertIn('keyEvent.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN', source)

    def test_game_mode_only_disables_new_power_sequences(self):
        source = (HOOK / 'GestureManager.kt').read_text()
        self.assertIn('allowNewSequence = !GameModeManager.isActive', source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
