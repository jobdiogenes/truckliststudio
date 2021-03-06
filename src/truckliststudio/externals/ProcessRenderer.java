/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package truckliststudio.externals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import static truckliststudio.TrucklistStudio.audioFreq;
import static truckliststudio.TrucklistStudio.outFMEbe;
import static truckliststudio.TrucklistStudio.wsDistroWatch;
import static truckliststudio.externals.ProcessRenderer.ACTION.OUTPUT;
import truckliststudio.media.renderer.Exporter;
import truckliststudio.media.renderer.ProcessExecutor;
import truckliststudio.mixers.Frame;
import truckliststudio.mixers.MasterMixer;
import truckliststudio.streams.SinkFile;
import truckliststudio.streams.SinkUDP;
import truckliststudio.streams.Stream;
import truckliststudio.util.Tools;
import truckliststudio.util.Tools.OS;
import truckliststudio.media.renderer.Capturer;

/**
 *
 * @author patrick (modified by karl)
 */
public class ProcessRenderer {

    final static String RES_CAP = "capture_OS.properties";
    final static String RES_OUT = "output_OS.properties";
    private final static String userHomeDir = Tools.getUserHome();
    java.io.DataInput input = null;
    boolean stopMe = false;
    boolean stopped = true;
    private Properties plugins = null;
    String plugin = "";
    String oPlug = "output";
    String audioPulseInput = "";
    int videoPort = 0;
    int audioPort = 0;
    int frequency = audioFreq;
    int channels = 2;
    int bitSize = 16;
    Stream stream;
    ProcessExecutor processVideo;
    ProcessExecutor processAudio;
    Capturer captureC;
    Process pV = null;
    Process pA = null;
    Exporter exporter;
    FME fme = null;
    private final MasterMixer mixer = MasterMixer.getInstance();
    String distro = wsDistroWatch();

    public ProcessRenderer(Stream s, ACTION action, String plugin, String bkEnd) {
        stream = s;
//        System.out.println("BackEnd:"+bkEnd);

        if (bkEnd.equals("FF")) {
            if (action == OUTPUT) {
//                System.out.println("Action Output - BackEnd FF !!!");
                this.oPlug = "ffmpeg_output";
                this.plugin = plugin;
            } else {
//                System.out.println("Action Capture - BackEnd FF !!!");
                this.oPlug = "output";
                this.plugin = "ffmpeg_" + plugin;
                s.setComm("AV");
            }
        } else if (action == OUTPUT) {
            if (bkEnd.equals("AV")) {
                this.oPlug = "output";
                this.plugin = plugin;
            } else {
                this.oPlug = "gst_output";
                this.plugin = plugin;
            }
        } else if (distro.toLowerCase().equals("ubuntu") || distro.toLowerCase().equals("windows")) {
            this.plugin = plugin;
        } else if ("AV".equals(bkEnd) && plugin.equals("audiosource")) {
            this.plugin = "av_" + plugin;
        } else { //if ("AV".equals(bkEnd))
            this.plugin = plugin;
        }
//        System.out.println("OPlugin:"+oPlug);
//        System.out.println("Plugin: "+this.plugin);
        if (plugins == null) {
            plugins = new Properties();
            try {
                if (plugin.equals("custom")) {
                    plugins.load(stream.getFile().toURI().toURL().openStream());
//                    System.out.println("Plugins Custom: "+plugins);
                } else {
                    plugins.load(getResource(action).openStream());
                }
            } catch (IOException ex) {
                Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        processVideo = new ProcessExecutor(s.getName());
        processAudio = new ProcessExecutor(s.getName());

    }

    public ProcessRenderer(Stream s, FME fme, String plugin) {
        stream = s;
        this.plugin = plugin;
        if (outFMEbe == 0) {
            this.oPlug = "ffmpeg_output";
        } else if (outFMEbe == 1) {
            this.oPlug = "output";
        } else if (outFMEbe == 2) {
            this.oPlug = "gst_output";
        }
        this.fme = fme;
        if (plugins == null) {
            plugins = new Properties();
            try {
                plugins.load(getResource(ACTION.OUTPUT).openStream());
            } catch (IOException ex) {
                Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        processVideo = new ProcessExecutor(s.getName());
        processAudio = new ProcessExecutor(s.getName());

    }

    private String translateTag(String value) {
        String result = value.toUpperCase().replace('.', '_');
        if (plugins.containsKey("TAG_" + result)) {
            result = plugins.getProperty("TAG_" + result);
        }
        return result;
    }

    private URL getResource(ACTION a) throws MalformedURLException {

        File userSettings = null;
        switch (a) {
            case CAPTURE:
                userSettings = new File(new File(userHomeDir + "/.truckliststudio"), RES_CAP.replaceAll("OS", Tools.getOSName()));
                break;
            case OUTPUT:
                userSettings = new File(new File(userHomeDir + "/.truckliststudio"), RES_OUT.replaceAll("OS", Tools.getOSName()));
                break;
        }
        URL res = null;
        if (userSettings.exists()) {
            res = userSettings.toURI().toURL();
        } else {
            String path = null;
            switch (a) {
                case CAPTURE:
                    path = "/truckliststudio/externals/OS/sources/" + plugin + ".properties";
                    path = path.replaceAll("OS", Tools.getOSName());
                    break;
                case OUTPUT:
                    path = "/truckliststudio/externals/OS/outputs/" + oPlug + ".properties";
                    path = path.replaceAll("OS", Tools.getOSName());
                    break;
            }
            res = ProcessRenderer.class.getResource(path);
        }
//        System.out.println("Resource Used: " + res.toString());
        return res;
    }

    private String setParameters(String cmd) {
        String command = cmd;
        String fmeName = null;
        String fmeURL = null;
        if (fme != null) {
            fmeName = fme.getName();
            fmeURL = fme.getUrl();
        }
        for (Tags tag : Tags.values()) {
            switch (tag) {
                case VCODEC:
                    if (fme != null) {
                        command = command.replaceAll(Tags.VCODEC.toString(), translateTag(fme.getVcodec()));
                    }
                    break;
                case ACODEC:
                    if (fme != null) {
                        command = command.replaceAll(Tags.ACODEC.toString(), translateTag(fme.getAcodec()));
                    }
                    break;
                case VBITRATE:
                    if (fme != null) {
                        command = command.replaceAll(Tags.VBITRATE.toString(), fme.getVbitrate());
                    }
                    if (stream instanceof SinkFile || stream instanceof SinkUDP) {
                        command = command.replaceAll(Tags.VBITRATE.toString(), stream.getVbitrate());
                    }
                    break;
                case ABITRATE:
                    if (fme != null) {
                        command = command.replaceAll(Tags.ABITRATE.toString(), fme.getAbitrate());
                    }
                    if (stream instanceof SinkFile || stream instanceof SinkUDP) {
                        command = command.replaceAll(Tags.ABITRATE.toString(), stream.getAbitrate());
                    }
                    break;
                case URL:
                    if (fme != null) {
                        if (!fme.getMount().trim().isEmpty()) {
                            command = command.replaceAll(Tags.URL.toString(), "" + fmeURL);
                        } else {
                            command = command.replaceAll(Tags.URL.toString(), "" + fmeURL + "/" + fme.getStream()); // "\""+fme.getUrl()+"/"+fme.getStream()+" live=1 flashver=FME/2.520(compatible;20FMSc201.0)"+"\""
                        }
                    } else if (stream.getURL() != null) {
                        command = command.replaceAll(Tags.URL.toString(), "" + stream.getURL());
                    }
                    break;
                case MOUNT:
                    if (fme != null && fme.getMount() != "") {
                        command = command.replaceAll(Tags.MOUNT.toString(), "" + fme.getMount());
                    }
                case PASSWORD:
                    if (fme != null && fme.getPassword() != "") {
                        command = command.replaceAll(Tags.PASSWORD.toString(), "" + fme.getPassword());
                    }
                case KEYINT:
                    if (fme != null) {
                        command = command.replaceAll(Tags.KEYINT.toString(), "" + fme.getKeyInt());
                    } else {
                        command = command.replaceAll(Tags.KEYINT.toString(), "" + Integer.toString(5 * mixer.getRate()));
                    }
                case PORT:
                    if (fme != null && fme.getPort() != "") {
                        command = command.replaceAll(Tags.PORT.toString(), "" + fme.getPort());
                    }
                case APORT:
                    command = command.replaceAll(Tags.APORT.toString(), "" + audioPort);
                    break;
                case CHEIGHT:
                    command = command.replaceAll(Tags.CHEIGHT.toString(), "" + stream.getCaptureHeight());
                    break;
                case CWIDTH:
                    command = command.replaceAll(Tags.CWIDTH.toString(), "" + stream.getCaptureWidth());
                    break;
                case FILE:
                    if (stream.getFile() != null) {
                        if (Tools.getOS() == OS.WINDOWS) {
                            if (stream.getComm() == "GS") {
                                String winFile = stream.getFile().getAbsolutePath().replaceAll("\\\\", "\\\\\\\\");
                                winFile = winFile.replaceAll("\\\\", "\\\\\\\\");
//                                System.out.println("WinFile="+winFile);
                                command = command.replaceAll(Tags.FILE.toString(), "\"" + winFile + "\"");
                            } else {
                                command = command.replaceAll(Tags.FILE.toString(), "\"" + stream.getFile().getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"");
                            }
                        } else if (stream.getFile().getAbsolutePath().contains("http")) {
                            command = command.replaceAll(Tags.FILE.toString(), "" + stream.getFile().getAbsolutePath().replace(userHomeDir + "/", "") + "");
                        } else {
                            String sFile = stream.getFile().getAbsolutePath().replaceAll(" ", "\\ ");
                            if (stream instanceof SinkFile) {
                                sFile = sFile.replaceAll(" ", "_");
                            }
                            command = command.replaceAll(Tags.FILE.toString(), "" + sFile + "");
                        }
                    }
                    break;
                case OHEIGHT:
                    command = command.replaceAll(Tags.OHEIGHT.toString(), "" + stream.getHeight());
                    break;
                case OWIDTH:
                    command = command.replaceAll(Tags.OWIDTH.toString(), "" + stream.getWidth());
                    break;
                case RATE:
                    command = command.replaceAll(Tags.RATE.toString(), "" + stream.getRate());
                    break;
                case SEEK:
                    command = command.replaceAll(Tags.SEEK.toString(), "" + stream.getSeek());
                    break;
                case VPORT:
                    command = command.replaceAll(Tags.VPORT.toString(), "" + videoPort);
                    break;
                case GSEFFECT:
                    command = command.replaceAll(Tags.GSEFFECT.toString(), "" + stream.getGSEffect());
                    break;
                case FREQ:
                    command = command.replaceAll(Tags.FREQ.toString(), "" + frequency);
                    break;
                case BITSIZE:
                    command = command.replaceAll(Tags.BITSIZE.toString(), "" + bitSize);
                    break;
                case CHANNELS:
                    command = command.replaceAll(Tags.CHANNELS.toString(), "" + channels);
                    break;
                case AUDIOSRC:
                    command = command.replaceAll(Tags.AUDIOSRC.toString(), "" + stream.getAudioSource() + "");
                    break;
                case PAUDIOSRC:
                    command = command.replaceAll(Tags.PAUDIOSRC.toString(), "" + audioPulseInput);
                    break;
            }
        }
        return command;
    }

    public Frame getFrame() { //changed to captureC for console capture
        if (captureC == null) {
            return null;
        } else {
            return captureC.getFrame();
        }
    }

    public void read() {
        stopped = false;
        stopMe = false;
        new Thread(new Runnable() {

            @Override
            public void run() {
                String cmdVideo = "";
                String cmdAudio = "";
                String commandVideo = null;
                String commandAudio = null;
                // System.out.println(plugins.keySet().toString());
                if (stream.hasVideo()) {
                    if ("AV".equals(stream.getComm())) {
                        commandVideo = plugins.getProperty("AVvideo").replaceAll("  ", " "); //AVvideoC
                    } else if (!"".equals(stream.getGSEffect())) {
                        commandVideo = plugins.getProperty("GSvideoFX").replaceAll("  ", " "); //Making sure there is no double spaces
                    } else {
                        commandVideo = plugins.getProperty("GSvideo").replaceAll("  ", " "); //Making sure there is no double spaces
                    }
                }

                if (stream.hasAudio()) {
                    if ("AV".equals(stream.getComm())) {
                        commandAudio = plugins.getProperty("AVaudio").replaceAll("  ", " "); //AVaudioC
                    } else {
                        commandAudio = plugins.getProperty("GSaudio").replaceAll("  ", " "); //Making sure there is no double spaces
                    }
                }
                if (commandVideo != null) {
                    commandVideo = commandVideo.replaceAll(" ", "ABCDE");
                    commandVideo = setParameters(commandVideo);
                    String[] parmsVideo = commandVideo.split("ABCDE");
                    try {
                        for (String p : parmsVideo) {
                            cmdVideo = cmdVideo + p + " ";
                        }
                        System.out.print("CommandVideo: " + cmdVideo + "\n");
                        pV = processVideo.executeC(parmsVideo);
                    } catch (IOException | InterruptedException e) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, e);
                    }
                } else {
                    processVideo = null;
                }
                if (commandAudio != null) {
                    commandAudio = commandAudio.replaceAll(" ", "ABCDE");
                    commandAudio = setParameters(commandAudio);
                    String[] parmsAudio = commandAudio.split("ABCDE");
                    try {
                        for (String p : parmsAudio) {
                            cmdAudio = cmdAudio + p + " ";
                        }
                        System.out.print("CommandAudio: " + cmdAudio + "\n");
                        pA = processAudio.executeC(parmsAudio);
                    } catch (IOException | InterruptedException e) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, e);
                    }
                } else {
                    processAudio = null;
                }
                captureC = new Capturer(stream, pV, pA);
            }
        }).start();
    }

    public void writeCom() {
        stopped = false;
        stopMe = false;
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    exporter = new Exporter(stream);
                } catch (SocketException ex) {
                    Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
                }
                File file;
                String batchCommand;
                stopped = false;
                stopMe = false;
                videoPort = exporter.getVideoPort();
                audioPort = exporter.getAudioPort();
                String command = plugins.getProperty(plugin).replaceAll("  ", " "); //Making sure there is no double spaces
                command = setParameters(command);
                System.out.println("Command Out: " + command);
                if (distro.toLowerCase().equals("windows")) {
                    file = new File(userHomeDir + "/.truckliststudio/" + "WSBro.bat");
                    FileOutputStream fos;
                    Writer dos = null;
                    try {
                        fos = new FileOutputStream(file);
                        dos = new OutputStreamWriter(fos);
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        dos.write(command + "\n");
                        dos.close();
                    } catch (IOException ex) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    file.setExecutable(true);
                    batchCommand = userHomeDir + "/.truckliststudio/" + "WSBro.bat";
                } else {
                    file = new File(userHomeDir + "/.truckliststudio/" + "WSBro.sh");
                    FileOutputStream fos;
                    Writer dos = null;
                    try {
                        fos = new FileOutputStream(file);
                        dos = new OutputStreamWriter(fos);
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        dos.write("#!/bin/bash\n");
                        dos.write(command + "\n");
                        dos.close();
                    } catch (IOException ex) {
                        Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    file.setExecutable(true);
                    batchCommand = userHomeDir + "/.truckliststudio/" + "WSBro.sh";
                }
                try {
                    if (stream.hasVideo()) {
                        processAudio = null;
                        processVideo.executeString(batchCommand);
                    } else if (stream.hasAudio()) {
                        processVideo = null;
                        processAudio.executeString(batchCommand);
                    }
                    //We don't need processAudio on export.  Only 1 process is required...
                } catch (IOException | InterruptedException e) {
                    Logger.getLogger(ProcessRenderer.class.getName()).log(Level.SEVERE, null, e);
                }
            }
        }).start();

    }

    public void pause() //Author Martijn Courteaux Code
    {
        if (processVideo != null) {
            captureC.vPause();
        }
        if (processAudio != null) {
            captureC.aPause();
        }
    }

    public void play() {
        if (processVideo != null) {
            captureC.vPlay();
        }
        if (processAudio != null) {
            captureC.aPlay();
        }
    }

    public void stop() {
        stopMe = true;
        stopped = true;
        if (captureC != null) {
            captureC.abort();
            captureC = null;
//            System.out.println(stream.getName()+" Capture Cleared ...");
        }
        if (exporter != null) {
            exporter.abort();
            exporter = null;
//            System.out.println(stream.getName()+" Export Cleared ...");
        }
        if (processVideo != null) {
            processVideo.destroy();
            processVideo = null;
//            System.out.println(stream.getName()+" Video Cleared ...");
        }
        if (processAudio != null) {
            processAudio.destroy();
            processAudio = null;
//            System.out.println(stream.getName()+" Audio Cleared ...");
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getFrequency() {
        return frequency;
    }

    public int getBitSize() {
        return bitSize;
    }

    public int getChannels() {
        return channels;
    }

    public enum ACTION {
        CAPTURE, OUTPUT
    }
}
