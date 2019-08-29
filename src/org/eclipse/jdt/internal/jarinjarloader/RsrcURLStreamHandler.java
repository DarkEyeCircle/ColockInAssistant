//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.eclipse.jdt.internal.jarinjarloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import org.eclipse.jdt.internal.jarinjarloader.RsrcURLConnection;

public class RsrcURLStreamHandler extends URLStreamHandler {
    private ClassLoader classLoader;

    public RsrcURLStreamHandler(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    protected URLConnection openConnection(URL u) throws IOException {
        return new RsrcURLConnection(u, this.classLoader);
    }

    protected void parseURL(URL url, String spec, int start, int limit) {
        String file;
        if(spec.startsWith("rsrc:")) {
            file = spec.substring(5);
        } else if(url.getFile().equals("./")) {
            file = spec;
        } else if(url.getFile().endsWith("/")) {
            file = url.getFile() + spec;
        } else {
            file = spec;
        }

        this.setURL(url, "rsrc", "", -1, (String)null, (String)null, file, (String)null, (String)null);
    }
}
