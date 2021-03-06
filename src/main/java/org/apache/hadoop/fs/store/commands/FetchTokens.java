/*
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

package org.apache.hadoop.fs.store.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.security.PrivilegedExceptionAction;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;

/**
 * Fetch delegation tokens 
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class FetchTokens extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(FetchTokens.class);

  public static final String USAGE =
      "Usage: fetchdt <file> [-renewer <renewer>] [-r] [-p] [-xmlfile file] <url1> ... <url999>\n"
          + "-r: require each filesystem to issue a token\n"
          + "";

  private static final String RENEWER = "renewer";

  private static final String REQUIRED = "r";

  public FetchTokens() {
    createCommandFormat(2, 999,
            REQUIRED, VERBOSE);
    addValueOptions(XMLFILE, DEFINE, RENEWER);
    getCommandFormat().addOptionWithValue(RENEWER);
    getCommandFormat().addOptionWithValue(XMLFILE);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = parseArgs(args);
    if (paths.size() < 2) {
      errorln(USAGE);
      return EXIT_USAGE;
    }
    addAllDefaultXMLFiles();

    final Configuration conf = new Configuration();
    final UserGroupInformation self = UserGroupInformation.getLoginUser();

    final Path tokenfile = new Path(paths.get(0));
    final String ropt = getOption(RENEWER);
    final String renewer = ropt != null ?
        ropt : self.getShortUserName();

    final List<String> urls = paths.subList(1, paths.size());
    final boolean required = hasOption(REQUIRED);

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);

    // qualify the FS so that what gets printed is absolute.
    FileSystem fs = tokenfile.getFileSystem(conf);
    Path dest = tokenfile.makeQualified(fs.getUri(), fs.getWorkingDirectory());

    println("Collecting tokens for %d filesystem%s to to %s",
        urls.size(),
        plural(urls.size()),
        dest);
    Credentials retrieved = self.doAs(
        new PrivilegedExceptionAction<Credentials>() {
          @Override
          public Credentials run() throws Exception {
            return saveTokens(conf, dest, renewer, required, urls);
          }
        });
    int n = retrieved.numberOfTokens();
    if (n > 0) {
      println("Saved %d token%s to %s", n, plural(n), dest);
    } else {
      println("No tokens collected, file %s unchanged", dest);
    }
    return 0;
  }

  private Credentials saveTokens(
      Configuration conf,
      Path dest,
      String renewer,
      boolean required,
      List<String> urls) throws IOException {

    Credentials cred = new Credentials();
    for (String url : urls) {
      Path path = new Path(url);
      try (DurationInfo ignored =
               new DurationInfo(LOG, "Fetching token for %s", path)) {

        FileSystem fs = path.getFileSystem(conf);
        URI fsUri = fs.getUri();
        LOG.debug("Acquired FS {}", fs);
        Token<?> token = fs.getDelegationToken(renewer);
        if (token != null) {
          println("Fetched token: %s", token);
          cred.addToken(token.getService(), token);
        } else {
          println("No token for %s", path);
          if (required) {
            throw new ExitUtil.ExitException(44,
                "No token issued by filesystem " + fsUri);
          }
        }
      }
    }
    // all the tokens are collected, so save
    try(DurationInfo ignored =
            new DurationInfo(LOG, "Saving tokens to %s", dest)) {
      cred.writeTokenStorageFile(dest, conf);
    }
    return cred;
  }

  private String plural(int n) {
    return n == 1 ? "" : "s";
  }

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new FetchTokens(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
