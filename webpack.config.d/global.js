if (typeof config.output !== 'undefined') {
    config.output.globalObject = `(typeof self !== 'undefined' ? self : this)`
}
config.resolve.fallback = {"stream": require.resolve("stream-browserify"), "buffer": require.resolve("buffer/")}
