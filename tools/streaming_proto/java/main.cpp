#include <stdio.h>

#include <iomanip>
#include <iostream>
#include <map>
#include <sstream>
#include <string>

#include "Errors.h"
#include "JavaProtoStreamCodeGenerator.h"
#include "stream_proto_utils.h"

using namespace android::stream_proto;
using namespace google::protobuf::io;
using namespace std;

/**
 *
 * Main.
 */
int
main(int argc, char const*const* argv)
{
    (void)argc;
    (void)argv;

    GOOGLE_PROTOBUF_VERIFY_VERSION;

    // Read the request
    CodeGeneratorRequest request;
    request.ParseFromIstream(&cin);

    JavaProtoStreamCodeGenerator codeGenerator;
    CodeGeneratorResponse response = codeGenerator.generate(request);

    // If we had errors, don't write the response. Print the errors and exit.
    if (ERRORS.HasErrors()) {
        ERRORS.Print();
        return 1;
    }

    // If we didn't have errors, write the response and exit happily.
    response.SerializeToOstream(&cout);
    return 0;
}
