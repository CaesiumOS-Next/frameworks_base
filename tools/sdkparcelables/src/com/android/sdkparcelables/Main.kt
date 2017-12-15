package com.android.sdkparcelables

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    if (args.size != 2) {
        usage()
    }

    val zipFile = ZipFile(args[0])

    val ancestorCollector = AncestorCollector(Opcodes.ASM6, null)

    for (entry in zipFile.entries()) {
        if (entry.name.endsWith(".class")) {
            val reader = ClassReader(zipFile.getInputStream(entry))
            reader.accept(ancestorCollector,
                    ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
    }

    val parcelables = ParcelableDetector.ancestorsToParcelables(ancestorCollector.ancestors)

    val outFile = File(args[1])
    val outWriter = outFile.bufferedWriter()
    for (parcelable in parcelables) {
        outWriter.write("parcelable ")
        outWriter.write(parcelable.replace('/', '.').replace('$', '.'))
        outWriter.write(";\n")
    }
    outWriter.flush()
}

fun usage() {
    System.err.println("Usage: <input jar> <output aidl>")
    System.exit(1)
}