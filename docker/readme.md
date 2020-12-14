# Local monitoring stack
A preconfigured local monitoring stack with Docker.          

Provides the following tools:
- Grafana
- Graphite
- Prometheus
- Zipkin
                 
Have Docker installed (preferably latest) and execute the following:
```
docker-compose up
```
        
# Prometheus
Web admin endpoint is exposed at http://localhost:9090

Push gateway is exposed at http://localhost:9091

By default, scrapes inner containers and external app running on port 8080 under prometheus endpoint `/actuator/prometheus`.
See [prometheus config](config/prometheus/prometheus.yml) for settings.
Check http://localhost:9090/targets to see if targets are configured well.

# Graphite
Graphite is exposed at http://localhost:3001

# Grafana
Grafana is exposed at http://localhost:3000

Datasources must be configured for Prometheus (http://prometheus:9090) and Graphite (http://graphite:80).
                             
# Zipkin
Zipkin is exposed at http://localhost:9411
