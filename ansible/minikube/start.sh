
choco install kubernetes-helm
choco install kustomize


# Linux
# curl -s "https://raw.githubusercontent.com/\
# kubernetes-sigs/kustomize/master/hack/install_kustomize.sh"  | bash
#


minikube start --kubernetes-version v1.17.4 --cpus 4 --memory 8192

minikube ssh
sudo mkdir /mnt/data
exit

git clone https://github.com/GoogleCloudPlatform/flink-on-k8s-operator.git