- testing of getPDF:
`./run_http_post.py http://localhost:8080/tl-vinwin-backend/vinwin/getPDF -j request-getPDF.json`
- testing of getData (avtokod-history):
`./run_http_post.py http://hub.msp-tl.ru/msp-sgw-ws/service/api/storage/getData -j request-getData-history.json -o history.json`
- testing of getData (offence-avtokod-sts):
`./run_http_post.py http://hub.msp-tl.ru/msp-sgw-ws/service/api/storage/getData -j request-getData-offence.json -o offence.json`
- testing of sendEmail:
`./run_http_post.py http://hub.msp-tl.ru/msp-sgw-ws/service/api/email/send -j request-sendEmail.json`
