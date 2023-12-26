import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("[+] Web a analizar: ");

        String targetWeb = scanner.nextLine();

        System.out.println("[+] Path absoluto del ZIP: ");

        String zipPath = scanner.nextLine();

        Set<String> uniqueWords = new HashSet<>();
        Set<String> uniquePasswords = new HashSet<>();

        LocalDateTime startCrawl = LocalDateTime.now();
        try {
            Document document = Jsoup.connect(targetWeb).get();
            String htmlContent = document.body().text();

            uniqueWords.addAll(Arrays.asList(extractWords(htmlContent)));

        } catch (IOException e) {
            e.printStackTrace();
        }
        LocalDateTime finishCrawl = LocalDateTime.now();
        Duration durationCrawl = Duration.between(startCrawl, finishCrawl);
        System.out.println("Duración del crawling: " + durationCrawl.toMillis() + " milisegundos");

        LocalDateTime startPasswordGen = LocalDateTime.now();
        for(String p: uniqueWords){
            uniquePasswords.addAll(generatePasswordPerWord(p));
        }
        LocalDateTime finishPasswordGen = LocalDateTime.now();
        Duration durationPasswordGen = Duration.between(startPasswordGen, finishPasswordGen);
        System.out.println("Duración de la generación de contraseñas: " + durationPasswordGen.toMillis() + " milisegundos");
        System.out.println("Número de contraseñas calculadas: " + uniquePasswords.size());

        LocalDateTime startZipExtraction = LocalDateTime.now();
        AtomicBoolean passwordFound = new AtomicBoolean(false);

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(uniquePasswords.size());

        for (String password : uniquePasswords) {
            executorService.submit(() -> {
                try {
                    if (!passwordFound.get()) {
                        ZipFile zipFile = new ZipFile(zipPath, password.toCharArray());
                        zipFile.extractAll(".");
                        System.out.println("Contraseña correcta: " + password);
                        passwordFound.set(true);
                    }
                } catch (ZipException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        Thread progressThread = new Thread(() -> {
            try {
                while (!latch.await(1, TimeUnit.SECONDS)) {
                    clearConsole();
                    double progress = 100.0 - (latch.getCount() * 100.0 / uniquePasswords.size());
                    System.out.printf("Progreso: %.2f%%\n", progress);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        progressThread.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
            progressThread.interrupt();
        }

        LocalDateTime finishZipExtraction = LocalDateTime.now();
        Duration durationZipExtraction = Duration.between(startZipExtraction, finishZipExtraction);

        if (!passwordFound.get()) {
            System.out.println("No se encontró la contraseña correcta.");
        }

        System.out.println("Duración de la extracción del ZIP: " + durationZipExtraction.toMillis() + " milisegundos");
    }

    private static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    private static Set<String> generatePasswordPerWord(String rawPass) {

        Set<String> result = new HashSet<>();

        StringBuilder sb;
        StringBuilder sby;
        for (int i = 0; i < 10; i++) {
            sb = new StringBuilder();
            sb.append(i);
            sb.append(rawPass);
            sb.append("_");
            for (int j = 2017; j < 2023; j++) {
                sby = new StringBuilder();
                sby.append(sb);
                sby.append(j);
                result.add(sby.toString());
            }
        }
        return result;
    }

    private static String[] extractWords(String text) {
        String regex = "\\b\\w{9,20}\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        StringBuilder matchedWords = new StringBuilder();
        while (matcher.find()) {
            matchedWords.append(matcher.group()).append(" ");
        }

        return matchedWords.toString().trim().split("\\s+");
    }
}