//
// Created by pablogamito on 2/14/24.
//

#ifndef AOSP_MAIN_FRAMEWORKS_BASE_JAVAPROTOSTREAMCODEGENERATOR_H
#define AOSP_MAIN_FRAMEWORKS_BASE_JAVAPROTOSTREAMCODEGENERATOR_H

#include "stream_proto_utils.h"
#include "string_utils.h"

using namespace android::stream_proto;
using namespace google::protobuf::io;
using namespace std;

class JavaProtoStreamCodeGenerator {
public:
    CodeGeneratorResponse generate(CodeGeneratorRequest request);
};

#endif // AOSP_MAIN_FRAMEWORKS_BASE_JAVAPROTOSTREAMCODEGENERATOR_H
