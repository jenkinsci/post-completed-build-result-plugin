package org.terracotta.jenkins.plugins.postcompleted;

import com.squareup.okhttp.OkHttpClient;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Extension
public class PostCompletedRunListener extends RunListener<Run> implements Describable<PostCompletedRunListener> {

  private final OkHttpClient client = new OkHttpClient();
  private final static Logger LOG = Logger.getLogger(PostCompletedRunListener.class.getName());

  public PostCompletedRunListener() {
    client.setConnectTimeout(30, TimeUnit.SECONDS);
    client.setReadTimeout(30, TimeUnit.SECONDS);
  }

  @Override
  public void onStarted(Run run, TaskListener listener) {
  }

  @Override
  public void onCompleted(Run run, @Nonnull TaskListener listener) {

    String urlToSubmitTo = getDescriptor().getUrlToSubmitTo();
    try {
      new URL(urlToSubmitTo);
    } catch (MalformedURLException e) {
      LOG.info("The url to submit to is not valid, please check your global configuration");
      return;
    }

    String resultUrl = Hudson.getInstance().getRootUrl() + run.getUrl();
    LOG.info(run.getParent().getName() + "#" + run.getNumber() + " is posting its result url : " + resultUrl + " to : " + urlToSubmitTo);
    byte[] body = new byte[0];
    try {
      body = createBody(resultUrl).getBytes("UTF-8");
      String result = post(new URL(urlToSubmitTo), body);
      LOG.info(result);
    } catch (IOException e) {
      LOG.warning(e.getMessage());
    }
  }

  private String post(URL url, byte[] body) throws IOException {
    HttpURLConnection connection = client.open(url);
    OutputStream out = null;
    InputStream in = null;
    try {
      // Write the request.
      connection.setRequestMethod("POST");
      out = connection.getOutputStream();
      out.write(body);
      out.close();

      // Read the response.
      if (connection.getResponseCode() != HttpURLConnection.HTTP_CREATED && connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("Unexpected HTTP response: "
                + connection.getResponseCode() + " " + connection.getResponseMessage() + " when posting the url to " + url);
      }
      in = connection.getInputStream();
      return readFirstLine(in);
    } finally {
      // Clean up.
      if (out != null) out.close();
      if (in != null) in.close();
    }
  }

  private String readFirstLine(InputStream in) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    return reader.readLine();
  }

  private String createBody(String url) {
    return "url=" + url;
  }

  public DescriptorImpl getDescriptor() {
    return new DescriptorImpl();
  }

  /**
   * Descriptor for {@link PostCompletedRunListener}. Used as a singleton.
   * The class is marked as public so that it can be accessed from views.
   */
  @Extension // This indicates to Jenkins that this is an implementation of an extension point.
  public static final class DescriptorImpl extends Descriptor<PostCompletedRunListener> {
    /**
     * To persist global configuration information,
     * simply store it in a field and call save().
     * <p/>
     * <p/>
     * If you don't want fields to be persisted, use <tt>transient</tt>.
     */
    private String urlToSubmitTo;

    /**
     * In order to load the persisted global configuration, you have to
     * call load() in the constructor.
     */
    public DescriptorImpl() {
      load();
    }

    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      // Indicates that this builder can be used with all kinds of project types
      return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
      return "Say hello world";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
      // To persist global configuration information,
      // set that to properties and call save().
      urlToSubmitTo = formData.getString("urlToSubmitTo");
      save();
      return super.configure(req, formData);
    }

    /**
     * The method name is bit awkward because global.jelly calls this method to determine
     * the initial state of the checkbox by the naming convention.
     */
    public String getUrlToSubmitTo() {
      return urlToSubmitTo;
    }
  }
}

