package org.eclipse.leshan.client.demo;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;

import com.cars.framework.secrets.DockerSecretLoadException;
import com.cars.framework.secrets.DockerSecrets;

//@Configuration
public class SecretsConfig {

  //File under src/main/resources/config/
  private final String DEFAULT_SECRETS_FILE = "configuration.json";

  // This bean will be used in non-local or no profiles
  //@Bean(name = "secrets")
  //@Profile(value = "!local")
  public Map<String, String> secrets() {
    try {
      return DockerSecrets.loadFromFile("secrets-file");
    } catch (DockerSecretLoadException e) {
      System.out.println("Secrets Load failed : " + e.getMessage());
    }
    return Collections.emptyMap();
  }

  // This bean will be used for 'local' profile
  //@Bean(name = "secrets")
  //@Profile(value = "local")
  public Map<String, String> localSecrets() {
    try {
      URL url = ClassLoader.getSystemResource(DEFAULT_SECRETS_FILE);
      System.out.println("File URL: " + System.getProperty("user.dir"));
      if (url != null) {
        return DockerSecrets.loadFromFile(new File(System.getProperty("user.dir")+"/"+DEFAULT_SECRETS_FILE));
        //return DockerSecrets.loadFromFile(new File(url.getPath()));
      } else {
        System.out.println("Secrets Load failed : No file at " + DEFAULT_SECRETS_FILE);
      }
    } catch (DockerSecretLoadException e) {
      System.out.println("Secrets Load failed : " + e.getMessage());
    }
    return Collections.emptyMap();
  }
}
