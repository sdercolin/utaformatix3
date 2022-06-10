if(typeof config.output !== 'undefined') {
    config.output.globalObject = `(typeof self !== 'undefined' ? self : this)`
}
