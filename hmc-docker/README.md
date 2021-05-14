# HMC fullstack docker :whale:

- [Prerequisites](#prerequisites)
- [HMC Scripts](#HMC)
- [License](#license)

## Prerequisites

- [Docker](https://www.docker.com)
- psql (Tested with version 12.4)
- `ccd-docker` environment configured and running,
see [Run `ccd-docker` containers](#Run-ccd-docker-containers) for details

### Environment Variables
- Ensure the relevant environment variables in `hmc-docker/bin/env_variables-all.txt` are set by running

    ```bash
    cd hmc-docker/bin
    source env_variables_all.txt
  ```


### IDAM Configuration

- Create HMC test roles, services and users using scripts located in the bin directory.

    Export following variables required for the scripts to run
    ```bash
    export IDAM_ADMIN_USER=<enter email>
    export IDAM_ADMIN_PASSWORD=<enter password>
    ```

    The value for `IDAM_ADMIN_USER` and `IDAM_ADMIN_PASSWORD` details can be found on [confluence](https://tools.hmcts.net/confluence/x/eQP3P)

    - To add idam client services (eg: `xuiwebapp`) :

    ```bash
      ./bin/add-idam-clients.sh
    ```

    - To add roles required to import ccd definition:

    ```bash
      ./bin/add-roles.sh
    ```

    - To add users:

    ```bash
      ./bin/add-users.sh
    ```

### Run `ccd-docker` containers
- Install and run CCD stack as advised [here](https://github.com/hmcts/ccd-docker).

    Please enable elasticsearch+logstash along with other ccd components.
    ```bash
    ./ccd enable backend sidam sidam-local sidam-local-ccd elasticsearch logstash
    ```

## HMC

Please run hmc docker as follows.
```
> cd hmc-docker
> docker-compose -f compose/hmc.yml up -d
```

### Compose branches

By default, tha HMC container will be running the `latest` tag, built from the `master` branch.  However, this behaviour can be changed by using the environment variable: `HMI_OUTBOUND_ADAPTER_TAG`.

#### Switch to a branch

To switch to a branch (e.g. `pr-126`): `set` the environment variable and update the containers:

```bash
> export HMI_OUTBOUND_ADAPTER_TAG=<branch>
> docker-compose -f compose/hmc.yml up -d
```

#### Revert to `master`

To revert to `master`: `unset` the environment variable and update the containers:

```bash
> unset HMI_OUTBOUND_ADAPTER_TAG
> docker-compose -f compose/hmc.yml up -d
```

## LICENSE

This project is licensed under the MIT License - see the [LICENSE](LICENSE.md) file for details.
