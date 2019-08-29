//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.eclipse.jdt.internal.jarinjarloader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.eclipse.jdt.internal.jarinjarloader.RsrcURLStreamHandlerFactory;

public class JarRsrcLoader {
    public JarRsrcLoader() {
    }

    public static void main(String[] args) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
        JarRsrcLoader.ManifestInfo mi = getManifestInfo();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL.setURLStreamHandlerFactory(new RsrcURLStreamHandlerFactory(cl));
        URL[] rsrcUrls = new URL[mi.rsrcClassPath.length];

        for(int jceClassLoader = 0; jceClassLoader < mi.rsrcClassPath.length; ++jceClassLoader) {
            String c = mi.rsrcClassPath[jceClassLoader];
            if(c.endsWith("/")) {
                rsrcUrls[jceClassLoader] = new URL("rsrc:" + c);
            } else {
                rsrcUrls[jceClassLoader] = new URL("jar:rsrc:" + c + "!/");
            }
        }

        URLClassLoader var7 = new URLClassLoader(rsrcUrls, (ClassLoader)null);
        Thread.currentThread().setContextClassLoader(var7);
        Class var8 = Class.forName(mi.rsrcMainClass, true, var7);
        Method main = var8.getMethod("main", new Class[]{args.getClass()});
        main.invoke((Object)null, new Object[]{args});
    }

    private static JarRsrcLoader.ManifestInfo getManifestInfo() throws IOException {
        Enumeration resEnum = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");

        while(resEnum.hasMoreElements()) {
            try {
                URL url = (URL)resEnum.nextElement();
                InputStream is = url.openStream();
                if(is != null) {
                    JarRsrcLoader.ManifestInfo result = new JarRsrcLoader.ManifestInfo((JarRsrcLoader.ManifestInfo)null);
                    Manifest manifest = new Manifest(is);
                    Attributes mainAttribs = manifest.getMainAttributes();
                    result.rsrcMainClass = mainAttribs.getValue("Rsrc-Main-Class");
                    String rsrcCP = mainAttribs.getValue("Rsrc-Class-Path");
                    if(rsrcCP == null) {
                        rsrcCP = "";
                    }

                    result.rsrcClassPath = splitSpaces(rsrcCP);
                    if(result.rsrcMainClass != null && !result.rsrcMainClass.trim().equals("")) {
                        return result;
                    }
                }
            } catch (Exception var7) {
                ;
            }
        }

        System.err.println("Missing attributes for JarRsrcLoader in Manifest (Rsrc-Main-Class, Rsrc-Class-Path)");
        return null;
    }

    private static String[] splitSpaces(String line) {
        if(line == null) {
            return null;
        } else {
            ArrayList result = new ArrayList();

            int lastPos;
            for(int firstPos = 0; firstPos < line.length(); firstPos = lastPos + 1) {
                lastPos = line.indexOf(32, firstPos);
                if(lastPos == -1) {
                    lastPos = line.length();
                }

                if(lastPos > firstPos) {
                    result.add(line.substring(firstPos, lastPos));
                }
            }

            return (String[])result.toArray(new String[result.size()]);
        }
    }

    private static class ManifestInfo {
        String rsrcMainClass;
        String[] rsrcClassPath;

        private ManifestInfo(ManifestInfo manifestInfo) {
        }
    }
}
