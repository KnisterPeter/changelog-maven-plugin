package com.sinnerschrader.maven.subversion.changelog_maven_plugin;

import java.io.FileReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @goal changelog
 * @phase process-sources
 */
public class ChangelogMojo extends AbstractMojo {

  /**
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * @parameter default-value="false"
   */
  private boolean skip;

  /**
   * @parameter default-value="${project.scm.connection}"
   * @required
   */
  private String repositoryUrl;

  /**
   * @parameter
   */
  private String username;

  /**
   * @parameter
   */
  private String password;

  /**
   * @parameter
   */
  private String mustMatch;

  /**
   * @parameter default-value="changelog"
   */
  private String propertyLines;

  /**
   * @parameter default-value="changelogXml"
   */
  private String propertyXml;

  /**
   * @parameter
   */
  private String xslt;

  private boolean shouldSkip() {
    return skip || Boolean.parseBoolean(project.getProperties().getProperty("changelog.skip", "false"));
  }

  public void execute() throws MojoExecutionException {
    if (!shouldSkip()) {
      Pattern pattern = null;
      if (mustMatch != null) {
        pattern = Pattern.compile(mustMatch);
      }

      StringBuilder lines = new StringBuilder();
      StringBuilder xml = new StringBuilder();
      try {
        getLog().info("Log svn repository: " + repositoryUrl);
        DAVRepositoryFactory.setup();
        String url = repositoryUrl;
        if (url.startsWith("scm:svn:")) {
          url = url.substring("scm:svn:".length());
        }
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        if (username != null && password != null) {
          ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password);
          repository.setAuthenticationManager(authManager);
        }
        Collection<SVNLogEntry> entries = null;
        try {
          entries = getLogEntries(repository);
        } catch (SVNAuthenticationException e) {
          String _username = System.console().readLine("Username: ");
          String _password = new String(System.console().readPassword("Password: "));
          ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(_username, _password);
          repository.setAuthenticationManager(authManager);
          entries = getLogEntries(repository);
        }
        xml.append("<log>");
        for (SVNLogEntry entry : entries) {
          if (pattern == null || pattern.matcher(entry.getMessage()).matches()) {
            lines.append(entry.getMessage()).append('\n');
            xml.append("<line>").append(entry.getMessage()).append("</line>");
          }
        }
        xml.append("</log>");
      } catch (SVNException e) {
        throw new MojoExecutionException("Failed to generate changelog", e);
      }
      project.getProperties().setProperty(propertyLines, lines.toString());
      if (xslt != null) {
        StringWriter writer = new StringWriter();
        try {
          TransformerFactory transformerFactory = TransformerFactory.newInstance();
          Transformer transformer = transformerFactory.newTransformer(new StreamSource(new FileReader(xslt)));
          transformer.transform(new StreamSource(new StringReader(xml.toString())), new StreamResult(writer));
        } catch (Exception e) {
          throw new MojoExecutionException("Failed to transform changelog", e);
        }
        project.getProperties().setProperty(propertyXml, writer.toString());
      } else {
        project.getProperties().setProperty(propertyXml, xml.toString());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Collection<SVNLogEntry> getLogEntries(SVNRepository repository) throws SVNException {
    long startRevision = SVNRevision.HEAD.getNumber();
    long endRevision = 1000;
    boolean changedPath = true;
    boolean strictNode = true;
    return repository.log(new String[] {}, null, startRevision, endRevision, changedPath, strictNode);
  }

}
