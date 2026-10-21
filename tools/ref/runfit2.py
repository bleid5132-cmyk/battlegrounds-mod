from fitpose import *
ref=json.load(open('ref_meas.json')); corr=json.load(open('corr.json')) if __import__('os').path.exists('corr.json') else {}; cal={float(k):v for k,v in json.load(open('cal.json')).items()}
def C(t): return cal[t]
P1=dict(top=C(1.5)['top'],mz=C(1.5)['mz'],x=C(1.5)['x'])
TV=dict(top=[0.45,0.5,0.74],mz=-0.35,x=[0,0,0,0,0,-12])
K=[(0.35,P1),(3.2,P1),(3.45,TV),(4.1,TV),(4.25,dict(top=[0.5,0.45,0.72],mz=-0.3,x=[0,0,0,0,0,-12])),
   (4.45,dict(top=[0.6,0.72,0.3],mz=-0.3,x=[0,0,0,0,0,-16])),(4.55,dict(top=[0.6,0.72,0.3],mz=-0.3,x=[0,0,0,0,0,-15])),
   (4.68,dict(top=[0.45,0.85,0.25],mz=-0.5,x=[0,0,0,0,0,-10])),(4.8,dict(top=[0.1,0.98,0.15],mz=-0.8,x=[0,0,0,0,0,-4]))]
ts=[k[0] for k in K]
def pri(t):
    top=[np.interp(t,ts,[k[1]['top'][i] for k in K]) for i in range(3)]
    return top,float(np.interp(t,ts,[k[1]['mz'] for k in K])),float(np.interp(t,ts,[k[1]['x'][5] for k in K]))
out={}; x0=np.array(C(1.5)['x'])
times=[round(0.35+0.05*i,2) for i in range(int(round((4.8-0.35)/0.05))+1)]
# стартуем с профиля (0.35 → вперёд)
for t in times:
    r=ref[str(t)]; top,mz,pz=pri(t)
    if t==1.5: x0=np.array(cal[1.5]['x'])
    c=corr.get(str(t),dict(dx=0,dy=0,da=0)); tipt=nrm(r['tip'])-np.array([c['dx'],c['dy']])/518
    y=solve3(tipt,r['ang']-c['da'],{},x0,top_hint=top,w_top=0.6,mz_hint=mz,w_mz=0.6,pz_prior=pz,pzw=1.5)
    tip,ang,R,_=measure2(y[:3],y[3:])
    out[t]=y.tolist(); x0=y
    print(t,np.round(y,1),'tip',np.round((tip-nrm(r['tip']))*518,1),'ang',round(ang-r['ang'],2),'top',np.round(R@[0,1,0],2))
json.dump(out,open('fit.json','w'))
