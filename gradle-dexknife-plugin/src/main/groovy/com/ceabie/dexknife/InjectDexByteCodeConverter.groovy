package com.ceabie.dexknife;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.ILogger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * proxy the DexByteCodeConverter that plugin 3.0.0 to add '--minimal-main-dex' options.
 *
 * @author ceabie
 */
public class InjectDexByteCodeConverter extends DexByteCodeConverter {
    public InjectDexByteCodeConverter(ILogger logger, TargetInfo targetInfo,
                                      JavaProcessExecutor javaProcessExecutor,
                                      boolean verboseExec, ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter);
    }

    /**
     * Converts the bytecode to Dalvik format
     *
     * @param inputs the input files
     * @param outDexFolder the location of the output folder
     * @param dexOptions dex options
     */
    public void convertByteCode(
            @NonNull Collection<File> inputs,
            @NonNull File outDexFolder,
            boolean multidex,
            @Nullable File mainDexList,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler processOutputHandler,
            int minSdkVersion)
            throws IOException, InterruptedException, ProcessException {
        println("DexKnife: convertByteCode >= 3.0.0")

    }
}
