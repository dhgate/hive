/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.hplsql;

import java.sql.Date;
import java.sql.Timestamp;

public class Utils {

  /**
   * Unquote string and remove escape characters inside the script 
   */
  public static String unquoteString(String s) {
	  if(s == null) {
		  return null;
	  }
	  
	  int len = s.length();
	  StringBuffer s2 = new StringBuffer(len);	  
	  
	  for (int i = 0; i < len; i++) {
		char ch = s.charAt(i);
		char ch2 = (i < len - 1) ? s.charAt(i+1) : 0;
		  
	    if((i == 0 || i == len -1) && (ch == '\'' || ch == '"'))
	      continue;
	    else
	    // \' and '' escape sequences
	    if((ch == '\\' && ch2 == '\'') || (ch == '\'' && ch2 == '\''))
	      continue;
	    
	    s2.append(ch);	
	  }
	  
	  return s2.toString();
  }

  /**
   * Quote string and escape characters - ab'c -> 'ab''c'
   */
  public static String quoteString(String s) {
    if(s == null) {
      return null;
    }    
    int len = s.length();
    StringBuilder s2 = new StringBuilder(len + 2).append('\'');
    
    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);
      s2.append(ch);
      if(ch == '\'') {
        s2.append(ch);
      }      
    }
    s2.append('\'');
    return s2.toString();
  }
  
  /**
   * Merge quoted strings: 'a' 'b' -> 'ab'; 'a''b' 'c' -> 'a''bc'
   */
  public static String mergeQuotedStrings(String s1, String s2) {
	  if(s1 == null || s2 == null) {
		  return null;
	  }
	  
	  int len1 = s1.length();
	  int len2 = s2.length();
	  
	  if(len1 == 0 || len2 == 0) {
		  return s1;
	  }
	  
	  return s1.substring(0, len1 - 1) + s2.substring(1);
  }
  
  /**
   * Convert String to Date
   */
  public static Date toDate(String s) {
    int len = s.length();
    if(len >= 10) {
      int c4 = s.charAt(4);
      int c7 = s.charAt(7);
      // YYYY-MM-DD
      if(c4 == '-' && c7 == '-') {
        return Date.valueOf(s.substring(0, 10));
      }
    }
    return null;    
  }
  
  /**
   * Convert String to Timestamp
   */
  public static Timestamp toTimestamp(String s) {
    int len = s.length();
    if(len >= 10) {
      int c4 = s.charAt(4);
      int c7 = s.charAt(7);
      // YYYY-MM-DD 
      if(c4 == '-' && c7 == '-') {
        // Convert DB2 syntax: YYYY-MM-DD-HH.MI.SS.FFF
        if(len > 19) {
          if(s.charAt(10) == '-') {
            String s2 = s.substring(0, 10) + ' ' + s.substring(11, 13) + ':' + s.substring(14, 16) + ':' + 
                s.substring(17);
            return Timestamp.valueOf(s2);
          }          
        }
        else if(len == 10) {
          s += " 00:00:00.000";
        }
        return Timestamp.valueOf(s);
      }
    }
    return null;    
  }
  
  /**
   * Compare two String values and return min or max 
   */
  public static String minMaxString(String s1, String s2, boolean max) {
    if(s1 == null) {
      return s2;
    } 
    else if(s2 == null) {
      return s1;
    }    
    int cmp = s1.compareTo(s2);
    if((max && cmp < 0) || (!max && cmp > 0)) {
      return s2;
    }
    return s1;
  }

  /**
   * Compare two Int values and return min or max 
   */
  public static Long minMaxInt(Long i1, String s, boolean max) {
    Long i2 = null;
    try {
      i2 = Long.parseLong(s);
    }
    catch(NumberFormatException e) {}
    if(i1 == null) {
      return i2;
    } 
    else if(i2 == null) {
      return i1;
    }    
    if((max && i1.longValue() < i2.longValue()) || (!max && i1.longValue() > i2.longValue())) {
      return i2;
    }
    return i1;
  }
  
  /**
   * Compare two Date values and return min or max 
   */
  public static Date minMaxDate(Date d1, String s, boolean max) {
    Date d2 = Utils.toDate(s);
    if(d1 == null) {
      return d2;
    } else if(d2 == null) {
      return d1;
    }    
    if((max && d1.before(d2)) || (!max && d1.after(d2))) {
      return d2;
    }
    return d1;
  }
  
  /**
   * Convert String array to a string with the specified delimiter
   */
  public static String toString(String[] a, char del) {
    StringBuilder s = new StringBuilder();
    for(int i=0; i < a.length; i++) {
      if(i > 0) {
        s.append(del);
      }
      s.append(a[i]);
    }
    return s.toString();
  }
  
  /**
   * Convert SQL datetime format string to Java SimpleDateFormat
   */
  public static String convertSqlDatetimeFormat(String in) {
    StringBuilder out = new StringBuilder();
    int len = in.length();
    int i = 0;
    while (i < len) {
      if (i + 4 <= len && in.substring(i, i + 4).compareTo("YYYY") == 0) {
        out.append("yyyy");
        i += 4;
      }
      else if (i + 2 <= len && in.substring(i, i + 2).compareTo("mm") == 0) {
        out.append("MM");
        i += 2;
      }
      else if (i + 2 <= len && in.substring(i, i + 2).compareTo("DD") == 0) {
        out.append("dd");
        i += 2;
      }
      else if (i + 4 <= len && in.substring(i, i + 4).compareToIgnoreCase("HH24") == 0) {
        out.append("HH");
        i += 4;
      }
      else if (i + 2 <= len && in.substring(i, i + 2).compareToIgnoreCase("MI") == 0) {
        out.append("mm");
        i += 2;
      }
      else if (i + 2 <= len && in.substring(i, i + 2).compareTo("SS") == 0) {
        out.append("ss");
        i += 2;
      }
      else {
        out.append(in.charAt(i));
        i++;
      }
    }
    return out.toString();
  }
  
  /**
   * Get the executable directory
   */
  public static String getExecDir() {
    String dir = Hplsql.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    if (dir.endsWith(".jar")) {   
      dir = dir.substring(0, dir.lastIndexOf("/") + 1);
    }
    return dir;
  }
  
  /**
   * Format size value specified in bytes
   */
  public static String formatSizeInBytes(long bytes, String postfix) {
    String out; 
    if (bytes < 1024) {
      out = bytes + " bytes";
    }
    else if (bytes < 1024 * 1024) {
      out = String.format("%.1f", ((float)bytes)/1024) + " KB";
    }
    else if (bytes < 1024 * 1024 * 1024) {
      out = String.format("%.1f", ((float)bytes)/(1024 * 1024)) + " MB";
    }
    else {
      out = String.format("%.1f", ((float)bytes)/(1024 * 1024 * 1024)) + " GB";
    }
    if (postfix != null && !postfix.isEmpty()) {
      out += postfix;
    }
    return out;
  }
  
  public static String formatSizeInBytes(long bytes) {
    return Utils.formatSizeInBytes(bytes, null);
  }
  
  /**
   * Format bytes per second rate
   */
  public static String formatBytesPerSec(long bytes, long msElapsed) {
    float bytesPerSec = ((float)bytes)/msElapsed*1000;
    return Utils.formatSizeInBytes((long)bytesPerSec, "/sec");
  }
}
