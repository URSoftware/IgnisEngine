package com.ignis.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import com.ignis.audioeditor.WavAudioProcessor;
import com.ignis.audioeditor.WavAudioProcessor.WavData;

import java.io.File;
import java.util.*;

/**
 * AudioTools - Ferramentas de manipulacao de audio programatico do IgnisEngine expostas ao MCP.
 * Integra-se ao WavAudioProcessor nativo.
 */
public final class AudioTools {

    private AudioTools() {}

    /**
     * Registra as ferramentas de audio diretamente no servidor MCP.
     *
     * @param server Servidor MCP sincronizado.
     * @param projectFolder Pasta raiz do projeto ativo.
     */
    public static void register(McpSyncServer server, File projectFolder) {

        // --- 1. Tool: read_wav_info ---
        Tool readWavInfo = Tool.builder()
            .name("read_wav_info")
            .description("Obtem metadados e informacoes tecnicas de um arquivo de audio WAV do projeto")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Caminho relativo do arquivo WAV no projeto (ex: assets/audio/jump.wav)")
                ),
                "required", List.of("filePath")
            ))
            .build();
        server.addTool(new SyncToolSpecification(readWavInfo, (exchange, args) -> {
            try {
                String path = (String) args.arguments().get("filePath");
                File wavFile = new File(projectFolder, path);
                if (!wavFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Arquivo WAV nao encontrado: " + path)), true, null, null);
                }

                WavData wavData = WavAudioProcessor.readWav(wavFile);
                Map<String, Object> info = new HashMap<>();
                info.put("duration", wavData.duration);
                info.put("sampleRate", wavData.format.getSampleRate());
                info.put("channels", wavData.format.getChannels());
                info.put("sampleSizeInBits", wavData.format.getSampleSizeInBits());
                
                return new CallToolResult(List.<Content>of(new TextContent("Metadados do audio: " + info.toString())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao ler audio: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 2. Tool: trim_wav ---
        Tool trimWav = Tool.builder()
            .name("trim_wav")
            .description("Corta um trecho de um audio WAV gerando um novo arquivo de audio")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "sourcePath", Map.of("type", "string", "description", "Caminho relativo do WAV de origem"),
                    "outputPath", Map.of("type", "string", "description", "Caminho relativo para salvar o WAV cortado"),
                    "startSec", Map.of("type", "number", "description", "Ponto de inicio do corte em segundos"),
                    "endSec", Map.of("type", "number", "description", "Ponto final do corte em segundos")
                ),
                "required", List.of("sourcePath", "outputPath", "startSec", "endSec")
            ))
            .build();
        server.addTool(new SyncToolSpecification(trimWav, (exchange, args) -> {
            try {
                String src = (String) args.arguments().get("sourcePath");
                String dest = (String) args.arguments().get("outputPath");
                double start = ((Number) args.arguments().get("startSec")).doubleValue();
                double end = ((Number) args.arguments().get("endSec")).doubleValue();

                File srcFile = new File(projectFolder, src);
                File destFile = new File(projectFolder, dest);

                if (!srcFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Arquivo WAV de origem nao encontrado.")), true, null, null);
                }

                WavData data = WavAudioProcessor.readWav(srcFile);
                byte[] trimmedPcm = WavAudioProcessor.trimPcm(data.pcmData, data.format, start, end);
                
                destFile.getParentFile().mkdirs();
                WavAudioProcessor.writeWav(trimmedPcm, data.format, destFile);

                return new CallToolResult(List.<Content>of(new TextContent("Audio cortado com sucesso e salvo em " + dest)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao cortar audio: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 3. Tool: apply_wav_fades ---
        Tool applyWavFades = Tool.builder()
            .name("apply_wav_fades")
            .description("Aplica efeitos lineares de fade-in e fade-out no audio WAV")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "sourcePath", Map.of("type", "string", "description", "Caminho do WAV de origem"),
                    "outputPath", Map.of("type", "string", "description", "Caminho para salvar o audio alterado"),
                    "fadeInSec", Map.of("type", "number", "description", "Duracao do fade-in em segundos"),
                    "fadeOutSec", Map.of("type", "number", "description", "Duracao do fade-out em segundos")
                ),
                "required", List.of("sourcePath", "outputPath", "fadeInSec", "fadeOutSec")
            ))
            .build();
        server.addTool(new SyncToolSpecification(applyWavFades, (exchange, args) -> {
            try {
                String src = (String) args.arguments().get("sourcePath");
                String dest = (String) args.arguments().get("outputPath");
                double fadeIn = ((Number) args.arguments().get("fadeInSec")).doubleValue();
                double fadeOut = ((Number) args.arguments().get("fadeOutSec")).doubleValue();

                File srcFile = new File(projectFolder, src);
                File destFile = new File(projectFolder, dest);

                if (!srcFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Origem nao encontrada.")), true, null, null);
                }

                WavData data = WavAudioProcessor.readWav(srcFile);
                byte[] fadedPcm = WavAudioProcessor.applyFades(data.pcmData, data.format, fadeIn, fadeOut);
                
                destFile.getParentFile().mkdirs();
                WavAudioProcessor.writeWav(fadedPcm, data.format, destFile);

                return new CallToolResult(List.<Content>of(new TextContent("Fades aplicados e audio salvo em " + dest)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao aplicar fades: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 4. Tool: mix_wav_tracks ---
        Tool mixWavTracks = Tool.builder()
            .name("mix_wav_tracks")
            .description("Combina e mixa multiplos audios WAV em posicoes temporais especificas em um master")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "trackPaths", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string"),
                        "description", "Lista de caminhos de arquivos WAV a serem mixados"
                    ),
                    "startTimes", Map.of(
                        "type", "array",
                        "items", Map.of("type", "number"),
                        "description", "Lista com o instante de tempo de inicio de cada track em segundos correspondente"
                    ),
                    "outputPath", Map.of("type", "string", "description", "Caminho relativo para salvar o WAV master")
                ),
                "required", List.of("trackPaths", "startTimes", "outputPath")
            ))
            .build();
        server.addTool(new SyncToolSpecification(mixWavTracks, (exchange, args) -> {
            try {
                List<?> rawPaths = (List<?>) args.arguments().get("trackPaths");
                List<?> rawTimes = (List<?>) args.arguments().get("startTimes");
                String dest = (String) args.arguments().get("outputPath");

                if (rawPaths.size() != rawTimes.size() || rawPaths.isEmpty()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Listas de arquivos e tempos de inicio devem possuir o mesmo tamanho e nao estarem vazias.")), true, null, null);
                }

                List<byte[]> pcmStreams = new ArrayList<>();
                List<Double> startTimes = new ArrayList<>();
                javax.sound.sampled.AudioFormat targetFormat = null;

                for (int i = 0; i < rawPaths.size(); i++) {
                    String trackPath = (String) rawPaths.get(i);
                    double startTime = ((Number) rawTimes.get(i)).doubleValue();

                    File file = new File(projectFolder, trackPath);
                    if (!file.exists()) {
                        return new CallToolResult(List.<Content>of(new TextContent("Arquivo da track nao encontrado: " + trackPath)), true, null, null);
                    }

                    WavData data = WavAudioProcessor.readWav(file);
                    pcmStreams.add(data.pcmData);
                    startTimes.add(startTime);
                    
                    if (targetFormat == null) {
                        targetFormat = data.format;
                    }
                }

                byte[] masterPcm = WavAudioProcessor.mixTracks(pcmStreams, startTimes, targetFormat);
                
                File destFile = new File(projectFolder, dest);
                destFile.getParentFile().mkdirs();
                WavAudioProcessor.writeWav(masterPcm, targetFormat, destFile);

                return new CallToolResult(List.<Content>of(new TextContent("Tracks mixadas com sucesso e salvas em " + dest)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao mixar audios: " + e.getMessage())), true, null, null);
            }
        }));
    }
}
