/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <ostream>
#include <string>

#include "utils/Unicode.h"

#include <string_view>

namespace android {

// Read only wrapper around basic C strings. Prevents excessive copying.
// StringPiece does not own the data it is wrapping. The lifetime of the underlying
// data must outlive this StringPiece.
//
// WARNING: When creating from std::basic_string<>, moving the original
// std::basic_string<> will invalidate the data held in a BasicStringPiece<>.
// BasicStringPiece<> should only be used transitively.

// TODO: s/StringPiece/std::string_view/g throughout this code and then remove this.
using StringPiece = std::string_view;

}  // namespace android

inline ::std::ostream& operator<<(::std::ostream& out, const std::u16string& str) {
  ssize_t utf8_len = utf16_to_utf8_length(str.data(), str.size());
  if (utf8_len < 0) {
    return out << "???";
  }

  std::string utf8;
  utf8.resize(static_cast<size_t>(utf8_len));
  utf16_to_utf8(str.data(), str.size(), &*utf8.begin(), utf8_len + 1);
  return out << utf8;
}
