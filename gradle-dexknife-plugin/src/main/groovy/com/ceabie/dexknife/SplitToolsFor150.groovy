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
import com.android.build.gradle.AndroidGradleOptions
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.sdklib.AndroidVersion
import org.gradle.api.Project

/**
 * the spilt tools for plugin 1.5.0.
 *
 * @author ceabie
 */
public class SplitToolsFor150 extends DexSplitTools {

    public static boolean isCompat() {
         if (getAndroidPluginVersion(com.android.builder.Version.ANDROID_GRADLE_PLUGIN_VERSION) < 300) {
             return true
         }

        return false
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
//        TransformTask proGuardTask
        TransformTask jarMergingTask

        String name = variant.name.capitalize()
        boolean minifyEnabled = variant.buildType.minifyEnabled

        // find the task we want to process
        project.tasks.matching {
            ((it instanceof TransformTask) && it.name.endsWith(name)) // TransformTask
        }.each { TransformTask theTask ->
            Transform transform = theTask.transform
            String transformName = transform.name

//            if (minifyEnabled && "proguard".equals(transformName)) { // ProGuardTransform
//                proGuardTask = theTask
//            } else
            if ("jarMerging".equalsIgnoreCase(transformName)) {
                jarMergingTask = theTask
            } else if ("dex".equalsIgnoreCase(transformName)) { // DexTransform
                dexTask = theTask
            }
        }

        if (dexTask != null && ((DexTransform) dexTask.transform).multiDex) {
            println("DexKnife: processing Task")

            dexTask.inputs.file DEX_KNIFE_CFG_PRO
            dexTask.inputs.file DEX_KNIFE_CFG_TXT

            dexTask.doFirst {
                startDexKnife()

                File mappingFile = variant.mappingFile
                DexTransform dexTransform = it.transform
                File adtMainDexList = dexTransform.mainDexListFile

                println("DexKnife: Adt Main: " + adtMainDexList)

                String pluginVersion = getAndroidGradlePluginVersion()
                int gradlePluginVersion = getAndroidPluginVersion(pluginVersion)
                int featureLevel = AndroidGradleOptions.getTargetFeatureLevel(project)
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

                File mergedJar = null

                // 非混淆的，从合并后的jar文件中提起mainlist；
                // 混淆的，直接从mapping文件中提取
                if (minifyEnabled) {
                    println("DexKnife-From Mapping: " + mappingFile)
                } else {
                    if (jarMergingTask != null) {
                        Transform transform = jarMergingTask.transform
                        def outputProvider = jarMergingTask.outputStream.asOutput()
                        mergedJar = outputProvider.getContentLocation("combined",
                                transform.getOutputTypes(),
                                transform.getScopes(), Format.JAR)
                    }

                    println("DexKnife-From MergedJar: " + mergedJar)
                }

                if (processMainDexList(project, minifyEnabled, mappingFile, mergedJar,
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

                    // 替换 AndroidBuilder
                    InjectAndroidBuilder.proxyAndroidBuilder(dexTransform,
                            dexKnifeConfig.additionalParameters,
                            adtMainDexList)

                }

                endDexKnife()
            }
        } else {
            System.err.println("DexKnife: process task error")
        }
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
        return (variant.getVariantData().getType().isForTesting());
    }

    private static int getMinSdk(VariantScope variantScope) {
        def version = variantScope.getMinSdkVersion()
        return version != null? version.getApiLevel(): 0;
    }

    private static int getTargetSdk(VariantScope variantScope) {
        def version = variantScope.getVariantConfiguration().getTargetSdkVersion()
        return version != null? version.getApiLevel(): 0;
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
        println("FeatureLevel:  " + AndroidGradleOptions.getTargetFeatureLevel(project))
        println("MinSdkVersion: " + getMinSdk(variantScope))
        println("TargetSdkVersion: " + getTargetSdk(variantScope))
        println("isLegacyMultiDexMode: " + isLegacyMultiDexMode(variantScope))

        println("isInstantRunSupported: " + config.isInstantRunSupported())
        println("targetDeviceSupportsInstantRun: " + targetDeviceSupportsInstantRun(config, project))
        println("getPatchingPolicy: " + variantScope.getInstantRunBuildContext().getPatchingPolicy())
        System.err.println("Feedback Log End <<<<<<<<<<<<<<<<<<<<<<<<<<")
    }

    private static boolean targetDeviceSupportsInstantRun(
            GradleVariantConfiguration config,
            Project project) {
        if (config.isLegacyMultiDexMode()) {
            // We don't support legacy multi-dex on Dalvik.
            return AndroidGradleOptions.getTargetFeatureLevel(project) >=
                    AndroidVersion.ART_RUNTIME.getFeatureLevel();
        }

        return true;
    }
}