# VCenter Java Client
The VCenter Java Client class provides functionality to establish a secure connection with a VMware vCenter server and request authentication certificates.
# How to run the VCenter Client inside Java

Add VCenter in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
    <dependencies>
        <!-- [...] -->
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>${project.artifactId}</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
```

Invoke the VCenter Client:

```java
    public static void main(String[] args) throws Exception {

        final String vCenterName = "vcenter-hostname";
        final String username = "your-username";
        final String password = "your-password";
        final String esxHostname = "esx-hostname";

        VCenterClient.setDebug(() -> true, System.out::println);

        String sessionId = VCenterClient.requestCertificate(
            vCenterName,
            username,
            password,
            esxHostname
        );
        System.out.println("sessionId: " + sessionId);
    }
	
```
