# Jenkins Shared Library — Dev Branch

This branch contains the Jenkins Shared Library implementation and a Docker-based Jenkins build agent used for testing Maven, Java, Docker, Git and related CI/CD stages.

---

## 1. Architecture

```text
Jenkins Controller
       |
       | SSH :2222
       v
+-----------------------------+
| docker-maven-agent          |
| Ubuntu 24.04                |
|                             |
| Java 17                     |
| Maven                       |
| Docker CLI                  |
| Git                         |
| curl / jq / unzip           |
| OpenSSH Server              |
+-----------------------------+
       |
       | /var/run/docker.sock
       v
   Docker Host
```

The Jenkins controller connects to the Docker-based agent using SSH.

The agent uses the host Docker daemon through:

```text
/var/run/docker.sock
```

---

# 2. Start the Docker Jenkins Agent

Run the following command on the Docker host:

```bash
docker run -dit \ # Start an Ubuntu 24.04 container in detached interactive mode
  --name docker-maven-agent \ # Container name used by Jenkins agent configuration
  --network host \ # Use host networking so the agent is reachable through the host IP
  --privileged \ # Provides additional privileges required for Docker-related operations
  -v /var/run/docker.sock:/var/run/docker.sock \ # Allows the container to use the host Docker daemon
  -v jenkins-agent-workspace:/workspace \ # Persistent workspace volume for agent workloads
  ubuntu:24.04 # Ubuntu 24.04 base image
```

Verify the container:

```bash
docker ps # Verify that the Jenkins agent container is running
```

Enter the agent:

```bash
docker exec -it docker-maven-agent bash # Open a shell inside the Jenkins agent container
```

---

# 3. Install Build Dependencies

Run inside the `docker-maven-agent` container:

```bash
apt-get update # Refresh Ubuntu package metadata

apt-get install -y \ # Install required CI/CD tools
  openjdk-17-jdk \ # Java 17 required by the Maven application build
  maven \ # Maven build and dependency management tool
  docker.io \ # Docker CLI used to build and manage container images
  git \ # Git client used for source-code checkout
  curl \ # HTTP client useful for API and health-check operations
  jq \ # JSON processing utility commonly used in CI/CD scripts
  unzip # Utility for extracting ZIP archives
```

Verify the installed tools:

```bash
java -version # Verify Java installation
mvn -version # Verify Maven installation
docker --version # Verify Docker CLI installation
git --version # Verify Git installation
curl --version # Verify curl installation
jq --version # Verify jq installation
```

---

# 4. Configure Ubuntu Jenkins Agent

Enter the container:

```bash
docker exec -it docker-maven-agent bash # Open a shell inside the agent container
```

Install SSH server and Java:

```bash
apt-get update # Refresh package metadata

apt-get install -y \ # Install packages required for SSH-based Jenkins agent connection
  openssh-server \ # SSH server used by Jenkins to connect to the agent
  openjdk-17-jdk # Java runtime required by the Jenkins agent
```

Create the Jenkins user:

```bash
useradd -m -s /bin/bash jenkins # Create the Jenkins operating-system user
```

Prepare the SSH directory:

```bash
mkdir -p /home/jenkins/.ssh # Create SSH configuration directory for the Jenkins user

chown -R jenkins:jenkins /home/jenkins/.ssh # Give Jenkins ownership of the SSH directory

chmod 700 /home/jenkins/.ssh # Restrict SSH directory permissions
```

For password-based authentication:

```bash
passwd jenkins # Set a password for the Jenkins user
```

Configure SSH:

```bash
mkdir -p /run/sshd # Create runtime directory required by sshd

sed -i 's/^#Port 22/Port 2222/' /etc/ssh/sshd_config # Change SSH port from 22 to 2222
```

Start SSH:

```bash
/usr/sbin/sshd # Start the OpenSSH server
```

Verify:

```bash
ss -lntp | grep 2222 # Confirm that SSH is listening on port 2222
```

---

# 5. Test SSH From Jenkins Controller

Enter the Jenkins controller container:

```bash
docker exec -it <jenkins-container-name> bash # Open a shell inside the Jenkins controller
```

Install SSH client if required:

```bash
apt-get update # Refresh package metadata

apt-get install -y openssh-client # Install SSH client
```

Test connectivity:

```bash
ssh -p 2222 jenkins@192.168.1.42 # Connect to the Jenkins agent using SSH port 2222
```

Enter the configured Jenkins-user password when prompted.

Exit:

```bash
exit # Close the SSH session
```

---

# 6. Generate SSH Key for Passwordless Authentication

Passwordless SSH is recommended for Jenkins agent connections.

Run inside the Jenkins controller container:

```bash
ssh-keygen -t ed25519 -C "jenkins-agent" # Generate an Ed25519 SSH key pair for Jenkins agent authentication
```

The generated keys are normally stored under:

```text
/root/.ssh/
```

Check the public key:

```bash
cat /root/.ssh/id_ed25519.pub # Display the public key that will be added to the Jenkins agent
```

Check the private key:

```bash
cat /root/.ssh/id_ed25519 # Display the private key that will be configured in Jenkins credentials
```

> Keep the private key secure. Do not commit it to Git.

---

# 7. Configure Passwordless SSH on Agent

Enter the agent container:

```bash
docker exec -it docker-maven-agent bash # Open a shell inside the Jenkins agent
```

Switch to the Jenkins user:

```bash
su - jenkins # Switch from root to the Jenkins user
```

Create the SSH directory:

```bash
mkdir -p ~/.ssh # Create the Jenkins user's SSH directory

chmod 700 ~/.ssh # Set required SSH directory permissions
```

Create the authorized keys file:

```bash
nano ~/.ssh/authorized_keys # Add the Jenkins controller public key to this file
```

Paste the public key generated from the Jenkins controller:

```text
ssh-ed25519 AAAA... jenkins-agent
```

Set correct permissions:

```bash
chmod 600 ~/.ssh/authorized_keys # Restrict access to the authorized SSH keys

chown -R jenkins:jenkins ~/.ssh # Ensure Jenkins owns the SSH configuration
```

Exit the Jenkins user:

```bash
exit # Return to the root shell
```

Restart SSH if required:

```bash
/usr/sbin/sshd # Start SSH server if it is not already running
```

---

# 8. Test Passwordless SSH

From the Jenkins controller container:

```bash
ssh -i /root/.ssh/id_ed25519 -p 2222 jenkins@192.168.1.42 # Connect to the Jenkins agent using the generated private key
```

If configured correctly, Jenkins should connect without asking for the Jenkins-user password.

Exit:

```bash
exit # Close the SSH session
```

---

# 9. Add SSH Credential in Jenkins

Navigate to:

```text
Manage Jenkins
    > Credentials
        > System
            > Global credentials
                > Add Credentials
```

Configure:

```text
Kind:
SSH Username with private key

Username:
jenkins

Private Key:
Enter directly

Private Key:
<contents of /root/.ssh/id_ed25519>

ID:
docker-maven-agent-ssh
```

Get the private key from the Jenkins controller container:

```bash
docker exec <jenkins-container-name> cat /root/.ssh/id_ed25519 # Display the private SSH key configured for the Jenkins agent
```

Copy the complete key including:

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

> Never commit the private key into the shared-library repository.

---

# 10. Create Jenkins Node

Navigate to:

```text
Manage Jenkins
    > Nodes
        > New Node
```

Configure:

```text
Name:
docker-maven-agent

Type:
Permanent Agent
```

Click:

```text
Create
```

---

# 11. Configure Jenkins Node

Use the following configuration:

```text
Remote root directory:
/home/jenkins

Labels:
docker maven

Usage:
Only build jobs with label expressions matching this node

Launch method:
Launch agents via SSH

Host:
192.168.1.42

Credentials:
docker-maven-agent-ssh

Host Key Verification Strategy:
Manually trusted key Verification Strategy

Advanced > Port:
2222
```

The Jenkins pipeline can then target this agent using:

```groovy
agent {
    label 'docker maven'
}
```

The label allows Jenkins to schedule the pipeline on the Docker Maven agent.

---

# 12. Alternative: Password-Based SSH

For a simple local lab, password authentication can also be used.

Jenkins node configuration:

```text
Launch method:
Launch agents via SSH

Host:
192.168.1.42

Credentials:
Username with password

Username:
jenkins

Password:
<jenkins-user-password>

Port:
2222
```

For production-style Jenkins configuration, prefer SSH key authentication instead of storing an agent-user password.

---

# 13. Docker Socket Permission

Because the agent uses the host Docker daemon:

```text
/var/run/docker.sock
```

the Docker group GID inside the container may need to match the Docker group GID on the host.

Check the Docker group on the host:

```bash
getent group docker # Display the host Docker group and its GID
```

Example:

```text
docker:x:984:
```

Check the Docker group inside the agent container:

```bash
docker exec docker-maven-agent getent group docker # Check the Docker group GID inside the agent
```

If the GIDs are different, Docker commands executed from the agent may fail with permission errors.

Example:

```bash
groupmod -g 984 docker # Change the container Docker group GID to match the host Docker group GID
```

Then verify:

```bash
getent group docker # Confirm the Docker group now has the expected GID
```

Test Docker access from the agent:

```bash
docker exec -it docker-maven-agent docker ps # Verify that the Jenkins agent can communicate with the host Docker daemon
```

Expected result:

```text
CONTAINER ID   IMAGE        COMMAND       STATUS
...
```

---

# 14. Verify Jenkins Agent

After configuring the node, Jenkins should show:

```text
docker-maven-agent
        |
        +-- Online
        +-- Label: docker maven
        +-- Remote FS: /home/jenkins
        +-- SSH Port: 2222
```

A simple Jenkins test pipeline:

```groovy
pipeline {
    agent {
        label 'docker maven' // Run this pipeline on the Docker Maven Jenkins agent
    }

    stages {
        stage('Agent Verification') {
            steps {
                sh 'whoami' // Verify the operating-system user executing the Jenkins job
                sh 'hostname' // Display the Jenkins agent hostname
                sh 'java -version' // Verify Java availability
                sh 'mvn -version' // Verify Maven availability
                sh 'docker --version' // Verify Docker CLI availability
                sh 'git --version' // Verify Git availability
            }
        }
    }
}
```

---

# 15. Jenkins Shared Library — Dev Branch

The shared library is maintained in the `dev` branch.

Example repository configuration:

```text
Repository:
jenkins_shared_lib

Branch:
dev
```

The Jenkins Shared Library should reference the `dev` branch when testing new shared-library changes.

Typical Jenkins configuration:

```text
Manage Jenkins
    > System
        > Global Trusted Pipeline Libraries
```

Example:

```text
Name:
shared-lib

Default version:
dev

Retrieval method:
Modern SCM

SCM:
Git

Repository:
<shared-library-git-repository>
```

---

# 16. Development Workflow

```text
Developer
    |
    v
Git Repository
    |
    | dev branch
    v
Jenkins Shared Library
    |
    v
Jenkins Controller
    |
    | SSH :2222
    v
docker-maven-agent
    |
    +--> Git
    +--> Java 17
    +--> Maven
    +--> Docker
    +--> curl
    +--> jq
    +--> unzip
    |
    v
Build / Test / Docker Operations
```

---

# 17. Troubleshooting

### SSH connection refused

```bash
docker exec docker-maven-agent ss -lntp | grep 2222 # Check whether SSH is listening on port 2222
```

Start SSH:

```bash
docker exec docker-maven-agent /usr/sbin/sshd # Start the SSH server inside the Jenkins agent
```

---

### SSH authentication failure

Check authorized keys:

```bash
docker exec docker-maven-agent cat /home/jenkins/.ssh/authorized_keys # Verify that the Jenkins controller public key exists on the agent
```

Check permissions:

```bash
docker exec docker-maven-agent ls -la /home/jenkins/.ssh # Verify SSH file ownership and permissions
```

Expected:

```text
.ssh                  -> 700
authorized_keys       -> 600
```

---

### Docker permission denied

Check host:

```bash
getent group docker # Get the host Docker group GID
```

Check agent:

```bash
docker exec docker-maven-agent getent group docker # Get the Docker group GID inside the Jenkins agent
```

The GIDs should match.

Test:

```bash
docker exec docker-maven-agent docker ps # Confirm Docker access from the Jenkins agent
```

---

### Jenkins agent offline

Check SSH manually:

```bash
ssh -i /root/.ssh/id_ed25519 -p 2222 jenkins@192.168.1.42 # Verify SSH connectivity independently of Jenkins
```

Check Java:

```bash
docker exec docker-maven-agent java -version # Jenkins SSH agent requires a working Java installation
```

Check Jenkins agent log:

```text
Manage Jenkins
    > Nodes
        > docker-maven-agent
            > Log
```

---

# 18. Important Security Notes

For this local development environment:

* `--privileged` gives the container elevated privileges.
* Mounting `/var/run/docker.sock` effectively gives the container control over the host Docker daemon.
* `--network host` removes normal Docker network isolation.
* `Non verifying Verification Strategy` should be avoided outside a local lab.
* SSH private keys must never be committed to Git.
* Prefer SSH key authentication over username/password authentication.
* For production Jenkins agents, use dedicated agents, restricted credentials, verified host keys and least-privilege access.

---

# 19. Quick Setup Summary

```text
1. Start docker-maven-agent
        ↓
2. Install Java/Maven/Docker/Git/tools
        ↓
3. Install OpenSSH server
        ↓
4. Create jenkins user
        ↓
5. Configure SSH port 2222
        ↓
6. Generate Jenkins controller SSH key
        ↓
7. Add public key to agent authorized_keys
        ↓
8. Test passwordless SSH
        ↓
9. Add private key to Jenkins Credentials
        ↓
10. Create docker-maven-agent Jenkins node
        ↓
11. Configure label: docker maven
        ↓
12. Verify Docker socket permissions/GID
        ↓
13. Run Jenkins Shared Library pipeline
```

## Environment

| Component             | Configuration          |
| --------------------- | ---------------------- |
| Agent OS              | Ubuntu 24.04           |
| Java                  | OpenJDK 17             |
| Build Tool            | Maven                  |
| Container Tool        | Docker                 |
| SCM                   | Git                    |
| SSH Port              | 2222                   |
| Jenkins User          | `jenkins`              |
| Agent Label           | `docker maven`         |
| Agent Root            | `/home/jenkins`        |
| Docker Socket         | `/var/run/docker.sock` |
| Shared Library Branch | `dev`                  |

############
## Note: getent group docker #on host and container gid mismatch get run problem together (host and container at same time ) >  groupmod -g 984 docker #run to correct id as  host has
#########
