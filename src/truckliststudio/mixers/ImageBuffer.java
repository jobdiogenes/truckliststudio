/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package truckliststudio.mixers;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import truckliststudio.util.Tools;

/**
 *
 * @author patrick
 */
public class ImageBuffer {
    private final ArrayList<TSImage> buffer = new ArrayList<>();
    private int bufferSize = MasterMixer.BUFFER_SIZE;
    private boolean abort = false;
    private int currentIndex = 0;
    private long framePushed = 0;
    private long framePopped = 0;
    
    
    public ImageBuffer(int w,int h){
        for (int i = 0;i<bufferSize;i++){
            TSImage img = new TSImage(w,h,BufferedImage.TYPE_INT_RGB);
            buffer.add(img);
        }
    }
    public ImageBuffer(int w,int h,int bufferSize){
        this.bufferSize=bufferSize;
        for (int i = 0;i<bufferSize;i++){
            TSImage img = new TSImage(w,h,BufferedImage.TYPE_INT_RGB);
            buffer.add(img);
        }
    }
    public void push(BufferedImage img){
        while(!abort && (framePushed - framePopped) >= bufferSize){
            Tools.sleep(30);
        }
        currentIndex++;
        currentIndex %= bufferSize;
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        TSImage image = buffer.get(currentIndex);
        image.setData(data);
        framePushed++;
    }
    public void doneUpdate(){
        currentIndex++;
        currentIndex %= bufferSize;
        framePushed++;
    }
    public TSImage getImageToUpdate(){
        while(!abort && (framePushed - framePopped) >= bufferSize){
            Tools.sleep(30);
        }
        return buffer.get((currentIndex+1)%bufferSize);
    }
    public TSImage pop(){
        while(!abort && framePopped >= framePushed){
            Tools.sleep(10);
        }
        framePopped++;
        return buffer.get(currentIndex);
    }
    public void abort(){
        abort=true;
        currentIndex=0;
    }
    public void clear(){
        abort=false;
        currentIndex=0;
    }
}
