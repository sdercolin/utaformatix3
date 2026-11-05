// Allow slower tests (e.g. performance benchmarks) to finish.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, {timeout: 60000});
