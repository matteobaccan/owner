#!/bin/sh
APP_DIR="$(dirname $0)/.."
cd $APP_DIR

#sed 's|url: http://owner.aeonbits.org|#url: http://owner.aeonbits.org|' _config.yml > _serve.yml

cat > _serve.yml <<-EOF
	url: http://localhost:4000
EOF

jekyll serve -w --config _config.yml,_serve.yml
rm _serve.yml
