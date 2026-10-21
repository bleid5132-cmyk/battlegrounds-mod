from fitpose import *
ref=json.load(open(f'{S}/ref_meas.json'))
def hint(t):
    K=[(0.35,(0.2,0.93,0.3),-0.3,-8.5),(3.3,(0.25,0.9,0.35),-0.3,-8.5),(3.45,(0.2,0.3,0.93),-0.4,-11),
       (4.1,(0.2,0.3,0.93),-0.4,-11),(4.4,(0.6,0.7,0.3),-0.45,-9.5),(4.8,(0.4,0.85,0.3),-0.4,-6)]
    ts=[k[0] for k in K]
    top=[np.interp(t,ts,[k[1][i] for k in K]) for i in range(3)]
    return top,float(np.interp(t,ts,[k[2] for k in K])),float(np.interp(t,ts,[k[3] for k in K]))
out={}
x0=np.array([0,40,-10,0,0,-8.0])
times=[round(0.35+0.05*i,2) for i in range(int((4.8-0.35)/0.05)+1)]
for t in times:
    r=ref[str(t)] if str(t) in ref else ref[f'{t}']
    tip=np.array([(r['tip'][0]-480)/518,(r['tip'][1]-281)/518])
    top,mz,pz=hint(t)
    x=solve(tip,r['ang'],top,mz,pz,x0)
    tp,an,R=measure(x[:3],x[3:])
    out[t]=x.tolist(); x0=x
    print(t, np.round(x,2), 'tip err px', np.round((tp-tip)*518,1), 'ang err', round(an-r['ang'],2))
json.dump(out,open(f'{S}/fit.json','w'))
