/*
 * Copyright (C) 2016 ceabie (https://github.com/ceabie/)
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
package com.ceabie.dexknife

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.dexing.DexingType
import com.android.sdklib.AndroidVersion
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

/**
 * the spilt tools for plugin 3.0.0.
 *
 * @author ceabie
 */
public class SplitToolsFor300 extends DexSplitTools {

    public static boolean isCompat() {
//         if (getAndroidPluginVersion() < 200) {
//             return true;
//         }

        return true;
    }

    public static void processSplitDex(Project project, ApplicationVariant variant) {
        VariantScope variantScope = variant.getVariantData().getScope()

        if (isInInstantRunMode(variantScope)) {
            System.err.println("DexKnife: Instant Run mode, DexKnife is auto disabled!")
            return
        }

        if (isInTestingMode(variant)) {
            System.err.println("DexKnife: Testing mode, DexKnife is auto disabled!")
            return
        }

        TransformTask dexTask
        TransformTask merginDexTask
        TransformTask jarMergingTask
        TransformTask dexBuilderTask

        println("DexKnife: processSplitDex ${variant.buildType.debuggable}")

        String name = variant.name.capitalize()
        boolean minifyEnabled = variant.buildType.minifyEnabled

        // find the task we want to process
        project.tasks.matching {
            ((it instanceof TransformTask) && it.name.endsWith(name)) // TransformTask
        }.each { TransformTask theTask ->
            Transform transform = theTask.transform
            String transformName = transform.name

            println("DexKnife: Task: " + transform)
//            theTask.inputs.files.each {
//                println("\t\t${it}")
//            }
//
//            println("-----------------------------------------------------------")
//
//            theTask.outputs.files.each {
//                println("\t\t${it}")
//            }
            if ("jarMerging".equalsIgnoreCase(transformName)) {
                jarMergingTask = theTask
            } else if ("dex".equalsIgnoreCase(transformName)) { // DexTransform
                dexTask = theTask
            } else if ("dexMerger".equalsIgnoreCase(transformName)) {
                dexTask = theTask
            } else if ("dexBuilder".equalsIgnoreCase(transformName)) {
                dexBuilderTask = theTask
            }
        }

        if (dexTask != null && dexTask.transform.dexingType != DexingType.MONO_DEX) {
            println("DexKnife: processing Task")

            dexTask.inputs.file DEX_KNIFE_CFG_PRO
            dexTask.inputs.file DEX_KNIFE_CFG_TXT

            dexTask.doFirst {
                startDexKnife()

                File mappingFile = variant.mappingFile
                def mainDexListFile = dexTask.transform.mainDexListFile
                File adtMainDexList
                if (mainDexListFile != null) {
                    Set<File> files = mainDexListFile.files
                    files.each {
                        adtMainDexList = it
                        println("DexKnife: Adt Main: " + it)
                    }
                }


                String pluginVersion = com.android.builder.Version.ANDROID_GRADLE_PLUGIN_VERSION
                int gradlePluginVersion = getAndroidPluginVersion(pluginVersion)
                int featureLevel = getTargetDeviceApi(variantScope)
                int minSdk = getMinSdk(variantScope)
                int targetSdk = getTargetSdk(variantScope)
                boolean isNewBuild = gradlePluginVersion >= 230 && featureLevel >= 23 && variant.buildType.debuggable

                println("DexKnife: AndroidPluginVersion: " + pluginVersion)
                println("          Target Device Api: " + featureLevel)
                if (isNewBuild) {
                    println("          MinSdkVersion: ${minSdk} (associated with Target Device Api and TargetSdkVersion)")
                } else {
                    println("          MinSdkVersion: ${minSdk}")
                }

                if (adtMainDexList == null) {
                    // Android Gradle Plugin >= 2.3.0，DeviceSDK >= 23时，MinSdkVersion与targetSdk、DeviceSDK有关。
                    // MinSdkVersion >= 21 时，Apk使用ART模式，系统支持mutlidex，并且不需要区分maindexlist，
                    // ART模式下，开启minifyEnabled时，会压缩dex的分包数量，否则使用pre-dex分包模式。
                    // MinSdkVersion < 21 时，Apk使用 LegacyMultiDexMode，maindexlist必然存在

                    if (isLegacyMultiDexMode(variantScope)) {
                        println("DexKnife: LegacyMultiDexMode")
                        logProjectSetting(project, variant, pluginVersion)
                    } else {
                        int artLevel = AndroidVersion.ART_RUNTIME.getFeatureLevel()
                        if (minSdk >= artLevel) {
                            System.err.println("DexKnife: MinSdkVersion (${minSdk}) >= ${artLevel} (System support ART Runtime).")
                            System.err.println("          Build with ART Runtime, MainDexList isn't necessary. DexKnife is auto disable!")

                            if (isNewBuild) {
                                System.err.println("")
                                System.err.println("          Note: In Android Gradle plugin >= 2.3.0 debug mode, MinSdkVersion is associated with min of \"Target Device (API ${featureLevel})\" and TargetSdkVersion (${targetSdk}).")
                                System.err.println("          If you want to enable DexKnife, use Android Gradle plugin < 2.3.0, or running device api < 23 or set TargetSdkVersion < 23.")
                            } else {
                                System.err.println("")
                                System.err.println("          If you want to use DexKnife, set MinSdkVersion < ${artLevel}.")
                            }

                            if (variant.buildType.debuggable) {
                                System.err.println("          Now is Debug mode. Make sure your MinSdkVersion < ${artLevel}, DexKnife will auto enable in release mode if conditions are compatible.")
                            }

                        } else {
                            logProjectSetting(project, variant, pluginVersion)
                        }
                    }

                    return
                }

                DexKnifeConfig dexKnifeConfig = getDexKnifeConfig(project)

                FileCollection allClasses = null

                // 非混淆的，从合并后的jar文件中提取mainlist，或从；
                // 混淆的，直接从mapping文件中提取
                if (minifyEnabled) {
                    println("DexKnife-From Mapping: " + mappingFile)
                } else {
                    if (jarMergingTask != null) {
                        Transform transform = jarMergingTask.transform
                        def outputProvider = jarMergingTask.outputStream.asOutput()
                        def mergingJar = outputProvider.getContentLocation("combined_classes",
                                transform.getOutputTypes(),
                                transform.getScopes(), Format.JAR)

                        allClasses = new SimpleFileCollection(mergingJar)
                        println("DexKnife-From MergedJar: " + mergingJar)
                    } else if (dexBuilderTask != null) {
                        allClasses = dexBuilderTask.inputs.files
                        println("DexKnife-From dexBuilderTask")
                    } else {
                        println("DexKnife-From null")
                    }
                }

                if (processMainDexList(project, minifyEnabled, mappingFile, allClasses,
                        adtMainDexList, dexKnifeConfig)) {

                    // replace android gradle plugin's maindexlist.txt
                    if (adtMainDexList != null) {
                        adtMainDexList.delete()
                        project.copy {
                            from MAINDEXLIST_TXT
                            into adtMainDexList.parentFile
                        }
                    } else {
                        adtMainDexList = project.file(MAINDEXLIST_TXT)
                    }

                    // after 2.2.0, it can additionalParameters, but it is a copy in task

//                    // 替换 AndroidBuilder
////                    InjectAndroidBuilder.proxyAndroidBuilder(dexTransform,
////                            dexKnifeConfig.additionalParameters,
////                            adtMainDexList)
//
                }

                endDexKnife()
            }
        } else {
            System.err.println("DexKnife: process task error")
        }
    }

    private static int getTargetDeviceApi(VariantScope scope) {
        scope.getVariantConfiguration().getMinSdkVersionWithTargetDeviceApi().getFeatureLevel()
    }

    private static boolean isInInstantRunMode(VariantScope scope) {
        try {
            def instantRunBuildContext = scope.getInstantRunBuildContext()
            return instantRunBuildContext.isInInstantRunMode()
        } catch (Throwable e) {
        }
        return false
    }

    private static boolean isInTestingMode(ApplicationVariant variant) {
        return (variant.getVariantData().getType().isForTesting())
    }

    private static int getMinSdk(VariantScope variantScope) {
        def version = variantScope.getMinSdkVersion()
        return version != null? version.getApiLevel(): 0
    }

    private static int getTargetSdk(VariantScope variantScope) {
        def version = variantScope.getVariantConfiguration().getTargetSdkVersion()
        return version != null? version.getApiLevel(): 0
    }

    private static boolean isLegacyMultiDexMode(VariantScope variantScope) {
        def configuration = variantScope.getVariantData().getVariantConfiguration()
        return configuration.isLegacyMultiDexMode()
    }

    private static void logProjectSetting(Project project, ApplicationVariant variant, String pluginVersion) {
        System.err.println("Please feedback below Log to  https://github.com/ceabie/DexKnifePlugin/issues")
        System.err.println("Feedback Log Start >>>>>>>>>>>>>>>>>>>>>>>")
        def variantScope = variant.getVariantData().getScope()
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();

        println("AndroidPluginVersion: " + pluginVersion)
        println("variant: " + variant.name.capitalize())
        println("minifyEnabled: " + variant.buildType.minifyEnabled)
        println("FeatureLevel:  " + getTargetDeviceApi(variantScope))
        println("MinSdkVersion: " + getMinSdk(variantScope))
        println("TargetSdkVersion: " + getTargetSdk(variantScope))
        println("isLegacyMultiDexMode: " + isLegacyMultiDexMode(variantScope))

        println("isInstantRunSupported: " + config.isInstantRunSupported())
        println("targetDeviceSupportsInstantRun: " + targetDeviceSupportsInstantRun(config, variantScope))
        println("getPatchingPolicy: " + variantScope.getInstantRunBuildContext().getPatchingPolicy())
        System.err.println("Feedback Log End <<<<<<<<<<<<<<<<<<<<<<<<<<")
    }

    private static boolean targetDeviceSupportsInstantRun(
            GradleVariantConfiguration config,
            VariantScope variantScope) {
        if (config.isLegacyMultiDexMode()) {
            // We don't support legacy multi-dex on Dalvik.
            return getTargetDeviceApi(variantScope) >=
                    AndroidVersion.ART_RUNTIME.getFeatureLevel()
        }

        return true;
    }
}