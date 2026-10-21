from fitpose import *
fit=json.load(open('fit.json')); ref=json.load(open('ref_meas.json'))
ANN={1.5:({'C':((601,318),1)},(0.2,0.93,0.3)),
     3.45:({'C':((484,404),1)},(0.45,0.5,0.74)),
     
     4.25:({'C':((564,458),1)},(0.5,0.4,0.75)),
     4.5:({'C':((669,417),1)},(0.6,0.75,0.25))}
cal={}
for t,(f,th) in ANN.items():
    r=ref[str(t)]; x0=np.array(fit[str(t)]); best=None
    for dz in (0,-5,5,-10):
        x=x0.copy(); x[5]+=dz
        y=solve3(nrm(r['tip']),r['ang'],f,x,top_hint=th,w_top=1.0,mz_hint=-0.35,w_mz=0.5)
        tip,ang,R,_=measure2(y[:3],y[3:])
        errs=[np.linalg.norm((proj((PIV+y[3:]+R@(FEAT[k]-PIV))[None])[0]-nrm(xy))*518) for k,(xy,w) in f.items()]
        c=sum(errs)+np.linalg.norm(R@[0,1,0]-np.array(th)/np.linalg.norm(th))*20
        if best is None or c<best[0]: best=(c,y,errs,R)
    c,y,errs,R=best; cal[t]=dict(x=y.tolist(),top=(R@[0,1,0]).tolist(),mz=float((R@[0,0,-1])[2]))
    print(t,np.round(y,2),'feat err',np.round(errs,1),'top',np.round(R@[0,1,0],2),'muz',np.round(R@[0,0,-1],2))
json.dump(cal,open('cal.json','w'))
