/*
 * Copyright 2011 YAMAZAKI Makoto<makoto1975@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zakky.tttc;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * 東北電力が公開している計画停電情報の PDF ファイルから、住所一覧を生成するプログラムです。
 */
public class PdfToAddressListMain {

    /**
     * 入力ファイルの拡張子一覧。
     */
    private static final String IN_EXTENSION = "pdf";

    /**
     * PDF から抽出した生テキストを保持するファイルの拡張子です。
     */
    private static final String TEMP_EXTENSION = "rawtxt";

    /**
     * 処理結果として作成されるファイルの拡張子です。
     */
    private static final String OUT_EXTENSION = "txt";

    /**
     * テキストファイルのエンコーディング
     */
    private static final String ENCODING = "UTF-8";

    /**
     * 生テキストで、番地を区切る際に使用されている文字(列)です。
     */
    private static final Pattern LOCAL_SEPARATOR_REGEXP = Pattern.compile("(，|,)");

    /**
     * メインメソッド。引き数で、処理対象のPDFファイルが置かれたディレクトリを指定してください。
     *
     * @param args 引き数。
     * @throws IOException ファイルの入出力が正常に行えなかった場合。
     * @throws InterruptedException スレッドが割り込まれた場合。
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final File targetDir;
        if (args.length == 0) {
            targetDir = new File(".");
            System.err.println("ターゲットディレクトリが指定されていないので、カレントディレクトリから PDF ファイルを探します: "
                    + targetDir.getAbsolutePath());
        } else {
            targetDir = new File(args[0]);
            System.err.println("以下の場所からから PDF ファイルを探します: " + targetDir.getAbsolutePath());
        }

        if (!targetDir.isDirectory()) {
            System.err.println("ターゲットがディレクトリではありません: " + targetDir.getPath());
            return;
        }

        for (File rawfile : listTargetFiles(targetDir)) {
            System.err.println("変換中: " + rawfile.getName());
            final String outStr = extractAddressLines(rawfile);
            final File outFile = replaceExtention(rawfile, OUT_EXTENSION);
            FileUtils.writeStringToFile(outFile, outStr, ENCODING);
            System.err.println("生成完了: " + outFile.getName());
        }
    }

    /**
     * 指定されたディレクトリ直下に存在する対象ファイル一覧を返します。
     *
     * @param targetDir 対象ファイルが置かれたディレクトリ。
     * @return 対象ファイル一覧。
     */
    private static Collection<File> listTargetFiles(final File targetDir) {
        @SuppressWarnings("unchecked")
        final Collection<File> files = FileUtils.listFiles(targetDir, new String[] {
            IN_EXTENSION
        }, false);
        return files;
    }

    /**
     * 拡張子を指定されたものに置き換えた {@link File} オブジェクトを返します。
     *
     * @param originalFile 元ファイル。
     * @param extention 拡張子。
     * @return 拡張子が変更された ファイルオブジェクト。
     */
    private static File replaceExtention(File originalFile, String extention) {
        final File parent = originalFile.getParentFile();
        final String name = originalFile.getName();

        final String baseName = FilenameUtils.getBaseName(name);

        return new File(parent, baseName + "." + extention);
    }

    /**
     * 指定された PDF ファイルからアドレスとグループ番号を改行区切りで保持する文字列を抽出します。
     *
     * @param pdffile もととなる PDF ファイル。
     * @return 抽出されたアドレスリスト文字列。
     * @throws IOException 入出力が正常に完了しなかった場合。
     * @throws InterruptedException 処理の途中でスレッドに割り込みがかかった場合。
     */
    private static String extractAddressLines(File pdffile) throws IOException,
            InterruptedException {
        final List<String> lines = readLines(pdffile);

        Integer groupNumber = null;
        String prefecture = null;
        String municipality = null;
        final StringBuilder localAddresses = new StringBuilder();
        final StringBuilder result = new StringBuilder();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || isPageNumberLine(line)) {
                continue;
            }

            final Integer currentGroupNumber = getGroupNumber(line, groupNumber);
            if (currentGroupNumber == null) {
                // グループ番号が登場するまではスキップ
                continue;
            }
            if (changed(groupNumber, currentGroupNumber)) {
                final List<String> addressLines = toAddressLines(groupNumber, prefecture,
                        municipality, localAddresses);
                for (String addressLine : addressLines) {
                    result.append(addressLine);
                    result.append('\n');
                }
                groupNumber = currentGroupNumber;
                localAddresses.setLength(0);
                continue;
            }

            final String currentPrefecture = getPrefecture(line, prefecture);
            if (currentPrefecture == null) {
                // 県名が登場するまではスキップ
                continue;
            }
            if (changed(prefecture, currentPrefecture)) {
                final List<String> addressLines = toAddressLines(groupNumber, prefecture,
                        municipality, localAddresses);
                for (String addressLine : addressLines) {
                    result.append(addressLine);
                    result.append('\n');
                }
                prefecture = currentPrefecture;
                localAddresses.setLength(0);
                continue;
            }

            final String currentMunicipality = getMunicipality(line, municipality);
            if (currentMunicipality == null) {
                throw new RuntimeException("市区町村名が取得できませんでした: " + line);
            }

            if (changed(municipality, currentMunicipality)) {
                final List<String> addressLines = toAddressLines(groupNumber, prefecture,
                        municipality, localAddresses);
                for (String addressLine : addressLines) {
                    result.append(addressLine);
                    result.append('\n');
                }
                prefecture = currentPrefecture;
                municipality = currentMunicipality;
                localAddresses.setLength(0);
                continue;
            }

            localAddresses.append(line);
        }

        final List<String> addressLines = toAddressLines(groupNumber, prefecture,
                municipality, localAddresses);
        for (String addressLine : addressLines) {
            result.append(addressLine);
            result.append('\n');
        }
        localAddresses.setLength(0);

        return result.toString();
    }

    /**
     * 指定されたグループ、県、市区町村のアドレス情報を {@link List} に変換します。
     *
     * @param groupNumber グループ番号文字列。 {@code null} の場合は空リストを返します。
     * @param prefecture 県名。 {@code null} の場合は空リストを返します。
     * @param municipality 市区町村名。 {@code null} の場合は空リストを返します。
     * @param localAddresses 番地がセパレータで区切られた文字列。
     * @return アドレス情報の {@link List}。
     */
    private static List<String> toAddressLines(Integer groupNumber, String prefecture,
            String municipality, CharSequence localAddresses) {
        if (groupNumber == null || prefecture == null || municipality == null
                || localAddresses.length() == 0) {
            assert localAddresses.length() == 0;
            return ImmutableList.<String> of();
        }
        final List<String> result = Lists.newArrayList();
        for (String local : localAddresses.toString().split(LOCAL_SEPARATOR_REGEXP.pattern())) {
            final String fullAddress = prefecture + municipality + local + " " + groupNumber;
            result.add(fullAddress);
        }

        return result;
    }

    /**
     * 渡された行が、ページ番号の行であるかどうかを判定します。
     *
     * @param line 対象行の文字列。
     * @return ページ番号行であれば　{@code true}、そうでなければ {@code false}。
     */
    private static boolean isPageNumberLine(String line) {
        if (line.isEmpty() || 2 < line.length()) {
            // ページ番号はいまのところ2桁まで
            return false;
        }
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c < '0' || '9' < c) {
                // ページ番号は半角の数字のみ
                return false;
            }
        }
        return true;
    }

    private static final String GROUP_LINE_PREFIX = "第";

    private static final String GROUP_LINE_SUFFIX = "グループ";

    /**
     * 対象行からグループ番号を取得します。
     *
     * @param line 対象行の文字列。
     * @param defaultGroupNumber 対象行がグループ番号行でない場合に返すグループ番号。
     * @return グループ番号。
     */
    private static Integer getGroupNumber(String line, Integer defaultGroupNumber) {
        if (!isGroupNumberLine(line)) {
            return defaultGroupNumber;
        }
        final String groupNumberStr = line.substring(GROUP_LINE_PREFIX.length(), line.length()
                - GROUP_LINE_SUFFIX.length());
        // 全角数字でも問題ないよー
        return Integer.valueOf(groupNumberStr);
    }

    /**
     * 対象行がグループ番号行かどうかを判定します。
     *
     * @param line 対象行の文字列。
     * @return グループ番号行であれば　{@code true}、そうでなければ {@code false}。
     */
    private static boolean isGroupNumberLine(String line) {
        return line.startsWith(GROUP_LINE_PREFIX) && line.endsWith(GROUP_LINE_SUFFIX);
    }

    private static final String PREF_LINE_PREFIX = "【";

    private static final String PREF_LINE_SUFFIX = "】";

    /**
     * 対象業から県名を取得します。
     *
     * @param line 対象行の文字列。
     * @param defaultGroupNumber 対象行が県名行でない場合に返す県名。
     * @return 県名。
     */
    private static String getPrefecture(String line, String defaultPrefecture) {
        if (!isPrefectureLine(line)) {
            return defaultPrefecture;
        }
        return line.substring(PREF_LINE_PREFIX.length(), line.length() - PREF_LINE_SUFFIX.length());
    }

    /**
     * 対象行が県名行であるかどうかを判定します。
     *
     * @param line 対象行の文字列。
     * @return 県名行であれば　{@code true}、そうでなければ {@code false}。
     */
    private static boolean isPrefectureLine(String line) {
        return line.startsWith(PREF_LINE_PREFIX) && line.endsWith(PREF_LINE_SUFFIX);
    }

    /**
     * 市区町村を取得します。
     *
     * @param line 対象行の文字列。
     * @param defaultMunicipality 対象の行データが市区町村ではない場合のデフォルト文字列。
     * @return 市区町村名。
     */
    private static String getMunicipality(String line, String defaultMunicipality) {
        if (!isMunicipalityLine(line)) {
            return defaultMunicipality;
        }
        return line;
    }

    /**
     * 対象行が市区町村名かどうかを判定します。
     *
     * @param line 対象行の文字列。
     * @return 市区町村名行であれば　{@code true}、そうでなければ {@code false}。
     */
    private static boolean isMunicipalityLine(String line) {
        final boolean meetsTailCondition = line.endsWith("市") || line.endsWith("区")
                || line.endsWith("町") || line.endsWith("村");
        if (!meetsTailCondition) {
            return false;
        }
        return !LOCAL_SEPARATOR_REGEXP.matcher(line).find();
    }

    /**
     * 変更があったかどうかを判定します。
     *
     * @param prev 以前のオブジェクト。
     * @param current 今のオブジェクト。
     * @return 変更があったかどうか。 {@code prev} が {@code null} の場合は無条件に変更ありと判定します。
     */
    private static boolean changed(Object prev, Object current) {
        return prev == null || !prev.equals(current);
    }

    /**
     * PDF の中のテキストを {@link String} の {@link List} として返します。
     *
     * @param pdffile PDF ファイル。
     * @return PDF から取り出した文字列をぎよう単位に分割した {@link List}。
     * @throws IOException 入出力が正常に完了しなかった場合。
     * @throws InterruptedException 処理の途中でスレッドが割り込まれた場合。
     */
    private static List<String> readLines(File pdffile) throws IOException, InterruptedException {
        final File outFile = replaceExtention(pdffile, TEMP_EXTENSION).getAbsoluteFile();
        if (outFile.isFile()) {
            outFile.delete();
        }

        final ProcessBuilder pb = new ProcessBuilder("/opt/local/bin/pdftotext",
                pdffile.getAbsolutePath(), outFile.getAbsolutePath());
        final Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new IOException("テキストの抽出に失敗しました: " + outFile);
        }

        @SuppressWarnings("unchecked")
        final List<String> lines = FileUtils.readLines(outFile, ENCODING);
        return lines;
    }
}
