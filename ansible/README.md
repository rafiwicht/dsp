# Ansible

Das Ganze ist zu der Zeit entstanden, wo ich Ansible neu gelernt habe. Aus diesem Grund sind einige Sachen nicht sauber gelöst. Ich würde folgendes Verbessern:

- Für Kubernetes die Idempotenz gewährleisten.
- Für das Deployment der Kubernetes Ressourcen entweder ein passenden Modul  oder als Alternative Terraform verwenden.
- Ich würde nicht mehr Docker als Container Runtime nehmen, da es in der neusten Version nicht mehr unterstützt wird.
