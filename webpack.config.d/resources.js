config.resolve.modules.push("src/main/resources");

config.module.rules.push({
    test: /\.vsqx$/i,
    loader: 'raw-loader'
}, {
    test: /\.vprjson$/i,
    loader: 'raw-loader'
}, {
    test: /\.svp$/i,
    loader: 'raw-loader'
}, {
    test: /\.s5p$/i,
    loader: 'raw-loader'
}, {
    test: /\.musicxml/i,
    loader: 'raw-loader'
}, {
    test: /\.ccs$/i,
    loader: 'raw-loader'
}, {
    test: /\.ustx/i,
    loader: 'raw-loader'
}, {
    test: /\.txt/i,
    loader: 'raw-loader'
}, {
    test: /\.(png|jpe?g|gif)$/i,
    use: [
        {
            loader: 'file-loader',
        },
    ],
});
