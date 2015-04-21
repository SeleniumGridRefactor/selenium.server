/*
Copyright 2012 Selenium committers
Copyright 2012 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package org.openqa.selenium.server.commands;

import org.openqa.jetty.http.HttpRequest;
import org.openqa.jetty.http.HttpResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class CaptureNetworkTrafficCommand extends Command {
  private static final List<Entry> entries = Collections.synchronizedList(new ArrayList<Entry>());

  public static void clear() {
    entries.clear();
  }

  public static void capture(Entry entry) {
    entries.add(entry);
  }

  private String type; // ie: XML, JSON, plain text, etc

  public CaptureNetworkTrafficCommand(String type) {
    this.type = type;
  }

  @Override
  public String execute() {
    StringBuilder sb = new StringBuilder();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    if ("json".equalsIgnoreCase(type)) {
      /*
       * 
       * [{ statusCode: 200, method: 'GET', url: 'http://foo.com/index.html', bytes: 12422, start:
       * '2009-03-15T14:23:00.000-0700', end: '2009-03-15T14:23:00.102-0700', timeInMillis: 102,
       * requestHeaders: [{ name: 'Foo', value: 'Bar' }], responseHeaders: [{ name: 'Baz', value:
       * 'Blah' }] },{ ... }]
       */

      sb.append("[");

      synchronized (entries) {
        for (final Iterator<Entry> iterator = entries.iterator(); iterator.hasNext();) {
          final Entry entry = iterator.next();
          sb.append("{\n");

          sb.append(jsonKey("statusCode")).append(entry.statusCode).append(",\n");
          sb.append(jsonKey("method")).append(json(entry.method)).append(",\n");
          sb.append(jsonKey("url")).append(json(entry.url)).append(",\n");
          sb.append(jsonKey("bytes")).append(entry.bytes).append(",\n");
          sb.append(jsonKey("start")).append(json(sdf.format(entry.start))).append(",\n");
          sb.append(jsonKey("end")).append(json(sdf.format(entry.end))).append(",\n");
          sb.append(jsonKey("timeInMillis")).append((entry.end.getTime() - entry.start.getTime()))
              .append(",\n");

          sb.append(jsonKey("requestHeaders")).append("[");
          jsonHeaders(sb, entry.requestHeaders);
          sb.append("],\n");

          sb.append(jsonKey("responseHeaders")).append("[");
          jsonHeaders(sb, entry.responseHeaders);
          sb.append("]\n");

          sb.append("}");

          if (iterator.hasNext()) {
            sb.append(",\n");
          }
        }
      }

      sb.append("]");
    } else if ("xml".equalsIgnoreCase(type)) {
      /*
       * <traffic> <entry statusCode="200" method="GET" url="http://foo.com/index.html"
       * bytes="12422" start="2009-03-15T14:23:00.000-0700" end="2009-03-15T14:23:00.102-0700"
       * timeInMillis="102"> <requestHeaders> <header name=""></header> </requestHeaders>
       * <responseHeaders> <header name=""></header> </responseHeaders> </entry> </traffic>
       */
      sb.append("<traffic>\n");

      synchronized (entries) {
        for (final Entry entry : entries) {
          sb.append("<entry ");

          sb.append("statusCode=\"").append(entry.statusCode).append("\" ");
          sb.append("method=\"").append(xml(entry.method)).append("\" ");
          sb.append("url=\"").append(xml(entry.url)).append("\" ");
          sb.append("bytes=\"").append(entry.bytes).append("\" ");
          sb.append("start=\"").append(sdf.format(entry.start)).append("\" ");
          sb.append("end=\"").append(sdf.format(entry.end)).append("\" ");
          sb.append("timeInMillis=\"").append((entry.end.getTime() - entry.start.getTime()))
              .append("\">\n");

          sb.append("    <requestHeaders>\n");
          xmlHeaders(sb, entry.requestHeaders);
          sb.append("    </requestHeaders>\n");

          sb.append("    <responseHeaders>\n");
          xmlHeaders(sb, entry.responseHeaders);
          sb.append("    </responseHeaders>\n");


          sb.append("</entry>\n");
        }
      }
      sb.append("</traffic>\n");
    } else {
      /*
       * 200 GET http://foo.com/index.html 12422 bytes 102ms (2009-03-15T14:23:00.000-0700 -
       * 2009-03-15T14:23:00.102-0700)
       * 
       * Request Headers - Foo => Bar Response Headers - Baz => Blah
       * ================================================================
       */

      synchronized (entries) {
        for (final Entry entry : entries) {
          sb.append(entry.statusCode).append(" ").append(entry.method).append(" ")
              .append(entry.url).append("\n");
          sb.append(entry.bytes).append(" bytes\n");
          sb.append(entry.end.getTime() - entry.start.getTime()).append("ms (")
              .append(sdf.format(entry.start)).append(" - ").append(sdf.format(entry.end))
              .append("\n");
          sb.append("\n");
          sb.append("Request Headers\n");
          for (Header header : entry.requestHeaders) {
            sb.append(" - ").append(header.name).append(" => ").append(header.value).append("\n");
          }
          sb.append("Response Headers\n");
          for (Header header : entry.responseHeaders) {
            sb.append(" - ").append(header.name).append(" => ").append(header.value).append("\n");
          }
          sb.append("================================================================\n");
          sb.append("\n");
        }
      }
    }

    clear();

    return "OK," + sb.toString();
  }

  private void xmlHeaders(final StringBuilder sb, final List<Header> headers) {
    for (final Header header : headers) {
      sb.append("        <header name=\"").append(xml(header.name)).append("\">")
          .append(xml(header.value)).append("</header>\n");
    }
  }

  private void jsonHeaders(final StringBuilder sb, final List<Header> headers) {
    for (final Iterator<Header> headItr = headers.iterator(); headItr.hasNext();) {
      final Header header = headItr.next();

      sb.append("{\n");
      sb.append("    ").append(jsonKey("name")).append(json(header.name)).append(",\n");
      sb.append("    ").append(jsonKey("value")).append(json(header.value)).append("\n");
      if (headItr.hasNext()) {
        sb.append("    },");
      }
      else {
        sb.append("  }");
      }

    }
  }

  private String xml(String s) {
    s = s.replaceAll("&", "&amp;");
    s = s.replaceAll("\"", "&quot;");
    s = s.replaceAll("\\<", "&lt;");
    s = s.replaceAll("\\>", "&gt;");

    return s;
  }

  private String jsonKey(final String key) {
    return "  \"" + key + "\"" + ":";
  }

  private Object json(String s) {
    if(s==null)
      return null;
    StringBuffer sb = new StringBuffer();
    sb.append("\"");
    escape(s, sb);
    sb.append("\"");
    return sb.toString();
  }

  // -------------- Copied from JSONValue -------------------
  /**
   * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters (U+0000 through U+001F).
   * @param s - Must not be null.
   * @param sb
   */
  private static void escape(String s, StringBuffer sb) {
    final int len = s.length();
    for(int i=0;i<len;i++){
      char ch=s.charAt(i);
      switch(ch){
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '/':
          sb.append("\\/");
          break;
        default:
          //Reference: http://www.unicode.org/versions/Unicode5.1.0/
          if((ch>='\u0000' && ch<='\u001F') || (ch>='\u007F' && ch<='\u009F') || (ch>='\u2000' && ch<='\u20FF')){
            String ss=Integer.toHexString(ch);
            sb.append("\\u");
            for(int k=0;k<4-ss.length();k++){
              sb.append('0');
            }
            sb.append(ss.toUpperCase());
          }
          else{
            sb.append(ch);
          }
      }
    }//for
  }
  // --------------------------------------------------------------

  public static class Entry {
    private String method;
    private String url;
    private int statusCode;
    private Date start;
    private Date end;
    private long bytes;
    private List<Header> requestHeaders = new ArrayList<Header>();
    private List<Header> responseHeaders = new ArrayList<Header>();

    public Entry(String method, String url) {
      this.method = method;
      this.url = url;
      this.start = new Date();
    }

    public void finish(int statusCode, long bytes) {
      this.statusCode = statusCode;
      this.bytes = bytes;
      this.end = new Date();
    }

    public void addRequestHeaders(HttpRequest request) {
      Enumeration names = request.getFieldNames();
      while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        String value = request.getField(name);

        requestHeaders.add(new Header(name, value));
      }
    }

    public void addResponseHeader(HttpResponse response) {
      Enumeration names = response.getFieldNames();
      while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        String value = response.getField(name);

        responseHeaders.add(new Header(name, value));
      }
    }

    public void setStart(Date start) {
      this.start = start;
    }

    public void setEnd(Date end) {
      this.end = end;
    }

    @Override
    public String toString() {
      return method + "|" + statusCode + "|" + url + "|" + requestHeaders.size() + "|" +
          responseHeaders.size() + "\n";
    }

    public void addRequestHeader(String key, String value) {
      this.requestHeaders.add(new Header(key, value));
    }
  }

  public static class Header {
    private String name;
    private String value;

    public Header(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }
}
