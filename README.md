# Como Executar o Sistema

Sistema de Monitoramento Ambiental com sensores IoT, Edge Server e Datacenter.

## Pré-requisitos

- **Java 21**
- **Maven 3.x**
- **tmux** (apenas para modo interativo)

## Compilação

```bash
# Compilar
mvn compile

# Limpar e compilar
mvn clean compile
```

## Modos de Execução

### 1. Modo Interativo (Recomendado)

Inicia todos os componentes em uma sessão tmux com 4 janelas:

```bash
./system.sh -i
```

**Layout das janelas:**

| Janela | Atalho | Conteúdo |
|--------|--------|----------|
| Serviços | `Ctrl+B 1` | Discovery \| Datacenter \| Edge |
| Sensores | `Ctrl+B 2` | SENSOR_001-004 (grid 2x2) |
| Edge | `Ctrl+B 3` | Edge log \| MaliciousSensor (manual) |
| Datacenter | `Ctrl+B 4` | Datacenter log \| ClientApp (manual) |

**Atalhos tmux:**

| Atalho | Ação |
|--------|------|
| `Ctrl+B <n>` | Ir para janela n |
| `Ctrl+B Setas` | Navegar entre painéis |
| `Ctrl+B Z` | Zoom no painel atual |
| `Ctrl+B [` | Modo scroll (Q para sair) |
| `Ctrl+B D` | Desanexar (processos continuam) |

**Para encerrar:** Feche o tmux ou pressione `Ctrl+B D` e depois `tmux kill-session -t system-monitor`

---

### 2. Execução Individual (Manual)

Executar cada componente em terminais separados, **na ordem indicada**:

**Passo 1 - Discovery Server (UDP:4000)**
```bash
mvn exec:java -Dexec.mainClass="com.project.server.discovery.ServerDiscovery"
```

**Passo 2 - Datacenter (TCP:8080, HTTP:9090)** *(aguardar 3s)*
```bash
mvn exec:java -Dexec.mainClass="com.project.server.datacenter.ServerDatacenter"
```

**Passo 3 - Edge Server (UDP:5000)** *(aguardar 2s)*
```bash
mvn exec:java -Dexec.mainClass="com.project.server.edge.ServerEdge"
```

**Passo 4 - Sensores** *(um por terminal)*
```bash
# Sensor 001
mvn exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" \
    -Dexec.args="SENSOR_001 senha123 localhost 4000"

# Sensor 002
mvn exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" \
    -Dexec.args="SENSOR_002 senha456 localhost 4000"

# Sensor 003
mvn exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" \
    -Dexec.args="SENSOR_003 senha789 localhost 4000"

# Sensor 004
mvn exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" \
    -Dexec.args="SENSOR_004 senha321 localhost 4000"
```

---

### 3. Aplicações Auxiliares

**ClientApp** - Cliente para consultar dados do Datacenter:
```bash
mvn exec:java -Dexec.mainClass="com.project.client.ClientApp"
```

**MaliciousSensor** - Testes de segurança:
```bash
mvn exec:java -Dexec.mainClass="com.project.client.sensor.MaliciousSensor"
```

---

## Scripts Auxiliares

```bash
# Parar todos os sensores
./scripts/stop-sensors.sh

# Parar e reiniciar sensores
./scripts/stop-sensors.sh restart
```

---

## Testando MaliciousSensor

1. Inicie o sistema: `./system.sh -i`
2. Vá para Janela 3: `Ctrl+B 3`
3. No painel direito, pressione `ENTER`
   - Os sensores serão parados automaticamente
   - O MaliciousSensor será iniciado
4. Após os testes, reinicie os sensores:
   ```bash
   ./scripts/stop-sensors.sh restart
   ```

---

## Acessando o Dashboard

Após iniciar o Datacenter, acesse:

```
http://localhost:9090
```

---

## Portas do Sistema

| Componente | Protocolo | Porta |
|------------|-----------|-------|
| Discovery | UDP | 4000 |
| Datacenter | TCP | 8080 |
| Datacenter | HTTP | 9090 |
| Edge | UDP | 5000 |

---

## Logs

Os logs ficam em `.system-pids/`:

```
.system-pids/
├── discovery.log
├── datacenter.log
├── edge.log
├── SENSOR_001.log
├── SENSOR_002.log
├── SENSOR_003.log
└── SENSOR_004.log
```
