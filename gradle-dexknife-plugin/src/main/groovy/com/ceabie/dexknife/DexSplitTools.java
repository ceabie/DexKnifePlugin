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
package com.ceabie.dexknife;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.specs.NotSpec;
import org.gradle.api.specs.OrSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * the base of spilt tools.
 *
 * @author ceabie
 */
public class DexSplitTools {

    public static final String DEX_KNIFE_CFG_TXT = "dexknife.txt";
    public static final String DEX_KNIFE_CFG_PRO = "dexknife.pro";

    private static final String DEX_MINIMAL_MAIN_DEX = "--minimal-main-dex";

    private static final String DEX_KNIFE_CFG_DEX_PARAM = "-dex-param";
    private static final String DEX_KNIFE_CFG_SPLIT = "-split";
    private static final String DEX_KNIFE_CFG_KEEP = "-keep";
    private static final String DEX_KNIFE_CFG_AUTO_MAINDEX = "-auto-maindex";
    private static final String DEX_KNIFE_CFG_DONOT_USE_SUGGEST = "-donot-use-suggest";
    private static final String DEX_KNIFE_CFG_LOG_MAIN_DEX = "-log-mainlist";
    private static final String DEX_KNIFE_CFG_FILTER_SUGGEST = "-filter-suggest";
    private static final String DEX_KNIFE_CFG_SUGGEST_SPLIT = "-suggest-split";
    private static final String DEX_KNIFE_CFG_SUGGEST_KEEP = "-suggest-keep";
    private static final String DEX_KNIFE_CFG_LOG_FILTER_SUGGEST = "-log-filter-suggest";
    private static final String DEX_KNIFE_CFG_LOG_FILTER = "-log-filter";

    protected static final String MAINDEXLIST_TXT = "maindexlist.txt";
    private static final String MAPPING_FLAG = " -> ";
    private static final int MAPPING_FLAG_LEN = MAPPING_FLAG.length();
    private static final String CLASS_SUFFIX = ".class";

    private static long StartTime = 0;

    protected static void startDexKnife() {
        System.out.println("DexKnife Processing ...");
        StartTime = System.currentTimeMillis();
    }

    protected static void endDexKnife() {
        String time;
        long internal = System.currentTimeMillis() - StartTime;
        if (internal > 1000) {
            float i = internal / 1000;
            if (i >= 60) {
                i = i / 60;
                int min = (int) i;
                time = min + " min " + (i - min) + " s";
            } else {
                time = i + "s";
            }
        } else {
            time = internal + "ms";
        }

        System.out.println("DexKnife Finished: " + time);
    }

    public static boolean processMainDexList(Project project, boolean minifyEnabled, File mappingFile,
                                             FileCollection allClasses, File andMainDexList,
                                             DexKnifeConfig dexKnifeConfig) throws Exception {

        if (!minifyEnabled && allClasses == null) {
            System.out.println("DexKnife Error: jarMerging is Null! Skip DexKnife. Please report All Gradle Log.");
            return false;
        }

        try {
            return genMainDexList(project, minifyEnabled, mappingFile, allClasses,
                    andMainDexList, dexKnifeConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean processMainDexList(Project project, boolean minifyEnabled, File mappingFile,
                                             File jarMergingOutputFile, File andMainDexList,
                                             DexKnifeConfig dexKnifeConfig) throws Exception {

        return processMainDexList(project, minifyEnabled, mappingFile,
                new SimpleFileCollection(jarMergingOutputFile),
                andMainDexList, dexKnifeConfig);
    }

    /**
     * get the config of dex knife
     */
    protected static DexKnifeConfig getDexKnifeConfig(Project project)
            throws Exception {
        File fileConfig = project.file(DEX_KNIFE_CFG_PRO);
        if (!fileConfig.exists()) {
            fileConfig = project.file(DEX_KNIFE_CFG_TXT);
        }

        BufferedReader reader = new BufferedReader(new FileReader(fileConfig));
        DexKnifeConfig dexKnifeConfig = new DexKnifeConfig();

        String line;
        boolean matchCmd;
        boolean minimalMainDex = true;
        Set<String> addParams = new HashSet<>();

        Set<String> splitToSecond = new HashSet<>();
        Set<String> keepMain = new HashSet<>();
        Set<String> splitSuggest = new HashSet<>();
        Set<String> keepSuggest = new HashSet<>();

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }

            int rem = line.indexOf('#');
            if (rem != -1) {
                if (rem == 0) {
                    continue;
                } else {
                    line = line.substring(0, rem).trim();
                }
            }

            String cmd = line.toLowerCase();
            matchCmd = true;

            if (DEX_KNIFE_CFG_AUTO_MAINDEX.equals(cmd)) {
                minimalMainDex = false;
            } else if (matchCommand(cmd, DEX_KNIFE_CFG_DEX_PARAM)) {
                String param = line.substring(DEX_KNIFE_CFG_DEX_PARAM.length()).trim();
                if (!param.toLowerCase().startsWith("--main-dex-list")) {
                    addParams.add(param);
                }

            } else if (matchCommand(cmd, DEX_KNIFE_CFG_SPLIT)) {
                String sPattern = line.substring(DEX_KNIFE_CFG_SPLIT.length()).trim();
                addClassFilePath(sPattern, splitToSecond);

            } else if (matchCommand(cmd, DEX_KNIFE_CFG_KEEP)) {
                String sPattern = line.substring(DEX_KNIFE_CFG_KEEP.length()).trim();
                addClassFilePath(sPattern, keepMain);

            } else if (DEX_KNIFE_CFG_DONOT_USE_SUGGEST.equals(cmd)) {
                dexKnifeConfig.useSuggest = false;

            } else if (DEX_KNIFE_CFG_FILTER_SUGGEST.equals(cmd)) {
                dexKnifeConfig.filterSuggest = true;

            } else if (DEX_KNIFE_CFG_LOG_MAIN_DEX.equals(cmd)) {
                dexKnifeConfig.logMainList = true;

            } else if (DEX_KNIFE_CFG_LOG_FILTER_SUGGEST.equals(cmd)) {
                dexKnifeConfig.logFilterSuggest = true;

            } else if (DEX_KNIFE_CFG_LOG_FILTER.equals(cmd)) {
                dexKnifeConfig.logFilter = true;

            } else if (matchCommand(cmd, DEX_KNIFE_CFG_SUGGEST_SPLIT)) {
                String sPattern = line.substring(DEX_KNIFE_CFG_SUGGEST_SPLIT.length()).trim();
                addClassFilePath(sPattern, splitSuggest);

            } else if (matchCommand(cmd, DEX_KNIFE_CFG_SUGGEST_KEEP)) {
                String sPattern = line.substring(DEX_KNIFE_CFG_SUGGEST_KEEP.length()).trim();
                addClassFilePath(sPattern, keepSuggest);

            } else if (!cmd.startsWith("-")) {
                addClassFilePath(line, splitToSecond);
            } else {
                matchCmd = false;
            }

            if (matchCmd) {
                System.out.println("DexKnife Config: " + line);
            }
        }

        reader.close();

        if (minimalMainDex) {
            addParams.add(DEX_MINIMAL_MAIN_DEX);
        }

        // 使用ADT建议的mainlist
        if (dexKnifeConfig.useSuggest) {

            // 将全局过滤应用到建议的mainlist
            if (dexKnifeConfig.filterSuggest) {
                splitSuggest.addAll(splitToSecond);
                keepSuggest.addAll(keepMain);
            }

            if (!splitSuggest.isEmpty() || !keepSuggest.isEmpty()) {
                dexKnifeConfig.suggestPatternSet = new PatternSet()
                        .exclude(splitSuggest)
                        .include(keepSuggest);
            }
        }

        if (!splitToSecond.isEmpty() || !keepMain.isEmpty()) {
            dexKnifeConfig.patternSet = new PatternSet()
                    .exclude(splitToSecond)
                    .include(keepMain);
        } else {
            if (!dexKnifeConfig.useSuggest) {
                System.err.println("DexKnife Warning: NO SET split Or keep path, it will use Recommend!");
                dexKnifeConfig.useSuggest = true;
            }
        }

        dexKnifeConfig.additionalParameters = addParams;

        return dexKnifeConfig;
    }

    private static boolean matchCommand(String text, String cmd) {
        Pattern pattern = Pattern.compile("^" + cmd + "\\s+");
        return pattern.matcher(text).find();
    }

    /**
     * add the class path to pattern list, and the single class pattern can work.
     */
    private static void addClassFilePath(String classPath, Set<String> patternList) {
        if (classPath != null && classPath.length() > 0) {
            if (classPath.endsWith(CLASS_SUFFIX)) {
                classPath = classPath.substring(0, classPath.length() - CLASS_SUFFIX.length())
                        .replace('.', '/') + CLASS_SUFFIX;
            } else {
                classPath = classPath.replace('.', '/');
            }

            patternList.add(classPath);
        }
    }

    private static Spec<FileTreeElement> getMaindexSpec(PatternSet patternSet) {
        Spec<FileTreeElement> maindexSpec = null;

        if (patternSet != null) {
            Spec<FileTreeElement> includeSpec = null;
            Spec<FileTreeElement> excludeSpec = null;

            if (!patternSet.getIncludes().isEmpty()) {
                includeSpec = patternSet.getAsIncludeSpec();
            }

            if (!patternSet.getExcludes().isEmpty()) {
                excludeSpec = patternSet.getAsExcludeSpec();
            }

            if (includeSpec != null && excludeSpec != null) {
                maindexSpec = new OrSpec<>(includeSpec, new NotSpec<>(excludeSpec));
            } else {
                if (excludeSpec != null) { // only exclude
                    maindexSpec = new NotSpec<>(excludeSpec);
                } else if (includeSpec != null) { //  only include
                    maindexSpec = includeSpec;
                }
            }
        }

//        if (maindexSpec == null) {
//            maindexSpec = Specs.satisfyAll();
//        }

        return maindexSpec;
    }


    private static boolean isPatternSetEmpty(PatternSet patternSet) {
        return patternSet.getExcludes().isEmpty() && patternSet.getIncludes().isEmpty()
                && patternSet.getExcludeSpecs().isEmpty() && patternSet.getIncludeSpecs().isEmpty();
    }

    /**
     * generate the main dex list
     */
    private static boolean genMainDexList(Project project, boolean minifyEnabled,
                                          File mappingFile, FileCollection allClasses,
                                          File adtMainDexList, DexKnifeConfig dexKnifeConfig) throws Exception {

        System.out.println(":" + project.getName() + ":genMainDexList");

        // 1.get the recommend adt's maindexlist
        Map<String, Boolean> recommendMainClasses = null;
        if (dexKnifeConfig.useSuggest && adtMainDexList != null && adtMainDexList.exists()) {

            PatternSet patternSet = dexKnifeConfig.suggestPatternSet;
            if (dexKnifeConfig.filterSuggest && patternSet == null) {
                patternSet = dexKnifeConfig.patternSet;
            }

            System.out.println("DexKnife: use suggest");
            recommendMainClasses = getRecommendMainDexClasses(adtMainDexList, patternSet, dexKnifeConfig.logFilterSuggest);
        }

        File keepFile = project.file(MAINDEXLIST_TXT);
        keepFile.delete();

        // 2.process the global filter
        List<String> mainClasses = null;
        if (dexKnifeConfig.patternSet == null || isPatternSetEmpty(dexKnifeConfig.patternSet)) {
            // only filter the suggest list
            if (recommendMainClasses != null && recommendMainClasses.size() > 0) {
                mainClasses = new ArrayList<>();
                Set<Map.Entry<String, Boolean>> entries = recommendMainClasses.entrySet();
                for (Map.Entry<String, Boolean> entry : entries) {
                    if (entry.getValue()) {
                        mainClasses.add(entry.getKey());
                    }
                }
            }
        } else {
            if (minifyEnabled) {
                System.err.println("DexKnife: From Mapping");
                // get classes from mapping
                mainClasses = getMainClassesFromMapping(mappingFile, dexKnifeConfig.patternSet,
                        recommendMainClasses, dexKnifeConfig.logFilter);
            } else {
                if (allClasses != null) {
                    System.out.println("DexKnife: From classes: " + allClasses.getAsPath());
                    // get main classes from project's classes
                    mainClasses = collectMainClassesFromFiles(allClasses, dexKnifeConfig.patternSet,
                            recommendMainClasses, dexKnifeConfig.logFilter);
                } else {
                    System.err.println("DexKnife: The classes is not exist! Can't be processed!");
                }
            }
        }

        // 3.create the miandexlist
        if (mainClasses != null && mainClasses.size() > 0) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(keepFile));

            for (String mainClass : mainClasses) {
                writer.write(mainClass);
                writer.newLine();

                if (dexKnifeConfig.logMainList) {
                    System.out.println(mainClass);
                }
            }

            writer.close();

            return true;
        }

        throw new Exception("DexKnife Warning: Main dex is EMPTY ! Check your config and project!");
    }

    /**
     * Gets main classes from jar.
     *
     * @param allClasses           the all classes path
     * @param mainDexPattern       the main dex pattern
     * @param adtMainCls           the filter mapping of suggest classes
     * @param logFilter
     * @return the main classes from jar
     * @throws Exception the exception
     * @author ceabie
     */
    private static ArrayList<String> collectMainClassesFromFiles(
            FileCollection allClasses, PatternSet mainDexPattern, Map<String, Boolean> adtMainCls, boolean logFilter)
            throws IOException {
        Spec<FileTreeElement> asSpec = getMaindexSpec(mainDexPattern);
        ArrayList<String> mainDexList = new ArrayList<>();

        ClassFileTreeElement treeElement = new ClassFileTreeElement();

        for (File file : allClasses) {
            if (file != null) {
                String name = file.getName();

                if (name.endsWith(".jar")) {
                    try {
                        collectClassesFromJar(adtMainCls, asSpec, new ZipFile(file), mainDexList, logFilter);
                        continue;
                    } catch (ZipException e) {
                    }
                }
                // from file path
                ClassParser classParser = new ClassParser(file.getAbsolutePath());
                JavaClass parse = classParser.parse();
                name = parse.getPackageName().replace('.', '/') + "/" + name;
//                System.out.println(name);
                collectMainClasses(adtMainCls, asSpec, mainDexList, treeElement, name, logFilter);
            }

        }


        return mainDexList;
    }

    private static void collectClassesFromJar(
            Map<String, Boolean> adtMainCls, Spec<FileTreeElement> asSpec, ZipFile clsFile,
            ArrayList<String> mainDexList, boolean logFilter)
            throws IOException {

        ClassFileTreeElement treeElement = new ClassFileTreeElement();

        // lists classes from jar.
        Enumeration<? extends ZipEntry> entries = clsFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            collectMainClasses(adtMainCls, asSpec, mainDexList, treeElement, entryName, logFilter);
        }

        clsFile.close();
    }

    private static void collectMainClasses(Map<String, Boolean> adtMainCls, Spec<FileTreeElement> asSpec,
                                           ArrayList<String> mainDexList, ClassFileTreeElement treeElement,
                                           String entryName, boolean logFilter) {
        if (entryName.endsWith(CLASS_SUFFIX)) {
            treeElement.setClassPath(entryName);
            if (isAtMainDex(adtMainCls, entryName, treeElement, asSpec, logFilter)) {
                mainDexList.add(entryName);
            }
        }
    }

    /**
     * Gets main classes from mapping.
     *
     * @param mapping        the mapping file
     * @param mainDexPattern the main dex pattern
     * @param recommendMainCls     the filter mapping of suggest classes
     * @param logFilter
     * @return the main classes from mapping
     * @throws Exception the exception
     * @author ceabie
     */
    private static List<String> getMainClassesFromMapping(
            File mapping,
            PatternSet mainDexPattern,
            Map<String, Boolean> recommendMainCls, boolean logFilter) throws Exception {

        String line;
        List<String> mainDexList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(mapping)); // all classes

        ClassFileTreeElement filterElement = new ClassFileTreeElement();
        Spec<FileTreeElement> asSpec = getMaindexSpec(mainDexPattern);

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.endsWith(":")) {
                int flagPos = line.indexOf(MAPPING_FLAG);
                if (flagPos != -1) {
                    String sOrgCls = line.substring(0, flagPos).replace('.', '/') + CLASS_SUFFIX;
                    String sMapCls = line.substring(flagPos + MAPPING_FLAG_LEN, line.length() - 1)
                            .replace('.', '/') + CLASS_SUFFIX;

                    filterElement.setClassPath(sOrgCls);

                    if (isAtMainDex(recommendMainCls, sMapCls, filterElement, asSpec, logFilter)) {
                        mainDexList.add(sMapCls);
                    }
                }
            }
        }

        reader.close();

        return mainDexList;
    }

    private static boolean isAtMainDex(
            Map<String, Boolean> mainCls, String sMapCls,
            ClassFileTreeElement treeElement, Spec<FileTreeElement> asSpec, boolean logFilter) {
        boolean isRecommend = false;

        // adt推荐
        if (mainCls != null) {
            Boolean value = mainCls.get(sMapCls);
            if (value != null) {
                isRecommend = value;
            }
        }

        // 全局过滤
        boolean inGlobalFilter = asSpec != null && asSpec.isSatisfiedBy(treeElement);

        if (logFilter) {
            String ret;
            if (isRecommend && inGlobalFilter) {
                ret = "true";
            } else if (isRecommend) {
                ret = "Recommend";
            } else if (inGlobalFilter) {
                ret = "Global";
            } else {
                ret = "false";
            }

            String s = "AtMainDex: " + treeElement.getPath() + " [" + ret + "]";

            if (isRecommend || inGlobalFilter) {
                System.err.println(s);
            } else {
                System.out.println(s);
            }
        }

        // 合并结果
        return isRecommend || inGlobalFilter;
    }

    /**
     * get the maindexlist of android gradle plugin.
     * if enable ProGuard, return the mapped class.
     */
    private static Map<String, Boolean> getRecommendMainDexClasses(
            File adtMainDexList,
            PatternSet mainDexPattern,
            boolean logFilter) throws Exception {

        if (adtMainDexList == null || !adtMainDexList.exists()) {
            System.err.println("DexKnife Warning: Android recommend Main dex is no exist.");
            return null;
        }

        HashMap<String, Boolean> mainCls = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(adtMainDexList));

        ClassFileTreeElement treeElement = new ClassFileTreeElement();
        Spec<FileTreeElement> asSpec = mainDexPattern != null ? getMaindexSpec(mainDexPattern) : null;

        String line, clsPath;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            int clsPos = line.lastIndexOf(CLASS_SUFFIX);
            if (clsPos != -1) {
                boolean satisfiedBy = true;

                if (asSpec != null) {
                    clsPath = line.substring(0, clsPos).replace('.', '/') + CLASS_SUFFIX;
                    treeElement.setClassPath(clsPath);

                    satisfiedBy = asSpec.isSatisfiedBy(treeElement);
                    if (logFilter) {
                        System.out.println("DexKnife-Suggest: [" +
                                (satisfiedBy ? "Keep" : "Split") + "]  " + clsPath);
                    }
                }

                mainCls.put(line, satisfiedBy);
            }
        }

        reader.close();

        if (mainCls.size() == 0) {
            mainCls = null;
        }

        return mainCls;
    }

    static int getAndroidPluginVersion(String version) {
        int size = version.length();
        int ver = 0;
        for (int i = 0; i < size; i++) {
            char c = version.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                if (c != '.') {
                    ver = ver * 10 + c - '0';
                }
            } else {
                break;
            }
        }

        return ver;
    }
}