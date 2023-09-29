# Copyright 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import pkgutil
import unittest

class TestLinterReports(unittest.TestCase):
    """Integration tests for the linters used by @EnforcePermission."""

    def test_no_aidl(self):
        report = pkgutil.get_data("lint", "lint-report.txt").decode()
        self.assertIn("MisusingEnforcePermissionAnnotation", report)
        self.assertIn("1 error", report)


if __name__ == '__main__':
    unittest.main(verbosity=2)
