## Example

docker build -t gitecsl/appstorage:1.0 .


docker tag localhost/gitecsl/apppostulante:1.0 registry.digitalocean.com/gitecsl-registry/appstorage:1.0

docker push registry.digitalocean.com/gitecsl-registry/appstorage:1.0

docker pull registry.digitalocean.com/gitecsl-registry/appstorage:1.0

docker run -d -p 8082:8082 registry.digitalocean.com/gitecsl-registry/appstorage:1.0