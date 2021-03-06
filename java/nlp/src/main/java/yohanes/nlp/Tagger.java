package yohanes.nlp;

import opennlp.tools.postag.*;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.*;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tagger {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(\"[^\"]*\"|'[^']*'|[^'\">])*>");
    private POSTaggerME tagger;

    public Tagger(String lang, String trainFile, String modelFile) throws Exception {
        InputStreamFactory isf = new MarkableFileInputStreamFactory(new File(trainFile));
        ObjectStream<String> lineStream = new PlainTextByLineStream(isf, "UTF-8");
        ObjectStream<POSSample> sampleStream = new WordTagSampleStream(lineStream);
        POSModel model = POSTaggerME.train(lang, sampleStream, TrainingParameters.defaultParams(), new POSTaggerFactory());
        this.tagger = new POSTaggerME(model);

        // if file doesnt exists, then create it
        File outFile = new File(modelFile);
        if (!outFile.exists()) {
            outFile.createNewFile();
        }
        OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile));
        model.serialize(modelOut);

        // close streams
        lineStream.close();
        modelOut.close();
    }

    public Tagger(InputStream modelIn) throws Exception {
        POSModel model = new POSModel(modelIn);
        this.tagger = new POSTaggerME(model);
    }


    public static String convertTrainString(String line, boolean firstWord) {
        String toWrite = null;
        Matcher matcher = TAG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            // read the word if it's not an xml tag
            // replace tab with underscore
            if (line.contains(" ")) {
                // split compound word to multiple words with same tag
                String[] temp = line.split("\t");
                String tag = temp[1];
                String[] words = temp[0].split(" ");
                for (int i = 0; i < words.length; i++) {
                    words[i] += "_" + tag;
                }
                toWrite = StringUtils.join(words, " ");
            } else {
                toWrite = line.replace("\t", "_");
            }
            if (!firstWord) {
                toWrite = " " + toWrite;
            }
        } else if (!firstWord) {
            // write newline every time we see an xml tag
            toWrite = "\n";
        }
        return toWrite;
    }

    public static void convertTrainFile(String rawTrainFile, String trainFile) throws IOException {
        BufferedReader br = null;
        String line;
        br = new BufferedReader(new FileReader(rawTrainFile));
        File outFile = new File(trainFile);

        // if file doesn't exists, then create it
        if (!outFile.exists()) {
            outFile.createNewFile();
        }

        FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);

        int count = 0;
        while ((line = br.readLine()) != null) {
            String toWrite = convertTrainString(line.trim(), count == 0);
            if (toWrite != null) {
                bw.write(toWrite);
                count++;
            }
        }

        // close streams
        if (br != null) br.close();
        if (bw != null) bw.close();
    }

    public static void convertAndSplitTrainFile(String rawTrainFile, String trainFile, String testFile, String testFileVerify, float trainProportion) throws Exception {
        BufferedReader br = null;
        String line;
        br = new BufferedReader(new FileReader(rawTrainFile));

        int count = 0;
        ArrayList<String> sentences = new ArrayList<String>();
        String sentence = "";
        while ((line = br.readLine()) != null) {
            String toWrite = convertTrainString(line, count == 0);
            if (toWrite != null) {
                if (toWrite == "\n") {
                    sentences.add(sentence.trim());
                    sentence = "";
                    count = 0;
                } else {
                    sentence += toWrite;
                }
                count++;
            }
        }

        // calculate number of required training data
        int numTrain = Math.round(trainProportion * sentences.size());

        // close read streams
        if (br != null) br.close();

        File outTrainFile = new File(trainFile);
        File outTestFile = new File(testFile);
        File outTestFileVerify = new File(testFileVerify);

        // if file does not exists, then create it
        if (!outTrainFile.exists()) {
            outTrainFile.createNewFile();
        }
        if (!outTestFile.exists()) {
            outTestFile.createNewFile();
        }
        if (!outTestFileVerify.exists()) {
            outTestFileVerify.createNewFile();
        }

        // write to files
        BufferedWriter trainWriter = new BufferedWriter(new FileWriter(outTrainFile.getAbsoluteFile()));
        BufferedWriter testWriter = new BufferedWriter(new FileWriter(outTestFile.getAbsoluteFile()));
        BufferedWriter testWriterVerify = new BufferedWriter(new FileWriter(outTestFileVerify.getAbsoluteFile()));
        Random rand = new Random();
        int countTrain = 0;
        for (int i = 0; i < sentences.size(); i++) {
            // randomize selection until enough number selected
            if ((countTrain < numTrain-1) && rand.nextBoolean()) {
                trainWriter.write(sentences.get(i).trim() + "\n");
                countTrain++;
            } else {
                testWriter.write(removeTagsFromSentence(sentences.get(i)).trim() + "\n");
                testWriterVerify.write(sentences.get(i).trim() + "\n");
            }
        }

        // close write streams
        if (trainWriter != null) trainWriter.close();
        if (testWriter != null) testWriter.close();
        if (testWriterVerify != null) testWriterVerify.close();
    }

//    public void trainPOSTaggerModel(String trainFile, String modelFile) throws IOException {
//        InputStreamFactory isf = new MarkableFileInputStreamFactory(new File(trainFile));
//        ObjectStream<String> lineStream = new PlainTextByLineStream(isf, "UTF-8");
//        ObjectStream<POSSample> sampleStream = new WordTagSampleStream(lineStream);
//        POSModel model = POSTaggerME.train("id", sampleStream, TrainingParameters.defaultParams(), new POSTaggerFactory());
//
//        // if file doesnt exists, then create it
//        File outFile = new File(modelFile);
//        if (!outFile.exists()) {
//            outFile.createNewFile();
//        }
//        OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile));
//        model.serialize(modelOut);
//
//        // close streams
//        lineStream.close();
//        modelOut.close();
//    }

    private static String removeTagsFromSentence(String sentence) throws Exception {
        String res = "";
        String[] words = sentence.split(" ");
        for (String w:words) {
            if (StringUtils.isNotEmpty(res)) res += " ";
            res += w.split("_")[0];
        }
        return res;
    }

    public String tagPOS(Tokenizer tokenizer, String line) {
        StringBuilder sb = new StringBuilder();
        if (line != null && StringUtils.isNotEmpty(line)) {
            String[] words = (tokenizer != null) ? tokenizer.tokenize(line) : line.split(" ");
            String[] tags = tagger.tag(words);
            // match words and tags and write it to output
            for (int i = 0; i < words.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(words[i]).append("_").append(tags[i]);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String[] tagPOS(String[] tokens) {
        return tagger.tag(tokens);
    }

    public void tagPOS(String testFile, String resultFile, boolean fromTrainData) throws IOException {
        String line;
        // if file doesnt exists, then create it
        File outFile = new File(resultFile);
        if (!outFile.exists()) {
            outFile.createNewFile();
        }
        FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);

        BufferedReader br = new BufferedReader(new FileReader(testFile));
        while ((line = br.readLine()) != null) {
            String tagged = (fromTrainData) ? this.tagPOS(null, line) : this.tagPOS(SimpleTokenizer.INSTANCE, line);
            if (tagged != null && StringUtils.isNotEmpty(tagged)) {
                bw.write(tagged);
            }
        }

        // close streams
        if (br != null) br.close();
        if (bw != null) bw.close();
    }

    public float calculateAccuracy(String taggedFile, String verifyFile) throws Exception {
        String line;
        BufferedReader taggedReader = new BufferedReader(new FileReader(taggedFile));
        BufferedReader verifyReader = new BufferedReader(new FileReader(verifyFile));

        ArrayList<String> taggedSentences = new ArrayList<String>();
        ArrayList<String> verifySentences = new ArrayList<String>();
        while ((line = taggedReader.readLine()) != null) if (StringUtils.isNotEmpty(line)) taggedSentences.add(line);
        while ((line = verifyReader.readLine()) != null) if (StringUtils.isNotEmpty(line)) verifySentences.add(line);

        int correct = 0;
        int total = 0;
        for (int i = 0; i < taggedSentences.size(); i++) {
            if (StringUtils.isNotEmpty(taggedSentences.get(i))) {
                String[] taggedArray = taggedSentences.get(i).split(" ");
                String[] verifyArray = verifySentences.get(i).split(" ");
                for (int j = 0; j < taggedArray.length; j++) {
                    String taggedTag = taggedArray[j].split("_")[1];
                    String verifyTag = verifyArray[j].split("_")[1];
                    if (taggedTag.equalsIgnoreCase(verifyTag)) correct++;
                    total++;
                }
            }
        }

        taggedReader.close();
        verifyReader.close();
        return correct / (float) total * 100;
    }

}
